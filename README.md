# Anaquel — sistema de préstamos para una biblioteca

Prueba técnica Fullstack Java.
Reemplaza la planilla de Excel por una aplicación real: catálogo consultable, préstamos con
reglas de atraso y bloqueo, lista de espera, correos automáticos y autocompletado de libros
desde Open Library con solo el ISBN.

| | |
|---|---|
| **Backend** | Java 21 · Spring Boot 3.3.5 (Web, Data JPA, Validation, Security, Mail, Cache) |
| **Base de datos** | PostgreSQL 16 · migraciones versionadas con Flyway |
| **Correo** | Spring Mail + plantillas Thymeleaf · MailHog en local |
| **API externa** | Open Library vía `WebClient` · caché Caffeine en memoria |
| **Frontend** | Angular 20 + TypeScript · componentes standalone · signals · GSAP |
| **Infraestructura** | Docker Compose (app + Postgres + MailHog + frontend) |
| **Pruebas** | **107 pruebas**: JUnit 5, Mockito, MockMvc, GreenMail, MockWebServer |

---

## 1. Levantarlo (un solo comando)

```bash
cp .env.example .env          # variables de entorno (ningún secreto real está versionado)
docker compose up --build
```

| Servicio | URL | Para qué |
|---|---|---|
| **Frontend** | http://localhost:5173 | La aplicación |
| **API** | http://localhost:8080 | Backend REST |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | Documentación viva de la API |
| **MailHog** | http://localhost:8025 | **Aquí se ven los correos que envía la aplicación** |
| **PostgreSQL** | localhost:5432 | Base de datos |

### Usuarios de prueba

Los crea la aplicación al arrancar ([`DataSeeder`](backend/src/main/java/com/jposada/anaquel/infrastructure/config/DataSeeder.java)),
con la contraseña que llegue en `SEED_ADMIN_PASSWORD`:

| Correo | Contraseña | Rol | Puede |
|---|---|---|---|
| `admin@anaquel.app` | `Admin123*` | ADMIN | Todo: registrar/eliminar libros, ver estadísticas, levantar bloqueos |
| `lectura@anaquel.app` | `Admin123*` | BIBLIOTECARIO | Ver el catálogo, pedir préstamos, devolver, reservar |

El catálogo arranca con 5 libros de ejemplo con **ISBN reales**
([`V2__seed_catalog.sql`](backend/src/main/resources/db/migration/V2__seed_catalog.sql)),
para poder probar el autocompletado de inmediato.

### Desarrollo sin Docker

```bash
# Solo la infraestructura
docker compose up -d db mailhog

# Backend  -> http://localhost:8080
cd backend && mvn spring-boot:run

# Frontend -> http://localhost:5173 (ng serve proxea /api al 8080, ver proxy.conf.json)
cd frontend && npm install && npm start
```

### Pruebas

```bash
cd backend && mvn test           # 107 pruebas: unitarias + integración
cd frontend && npm test          # 43 pruebas Jasmine + Karma (Chrome headless)
cd frontend && npm run build     # build de producción con chequeo estricto de plantillas
```

Las pruebas usan **H2 en memoria** y **GreenMail** (SMTP embebido): no hacen falta Docker,
Postgres ni credenciales para ejecutarlas.

> Al correr `mvn test` **en macOS** aparece un `ERROR` de Netty sobre
> `MacOSDnsServerAddressStreamProvider`. Es cosmético: Netty no encuentra la librería nativa
> opcional de resolución DNS y cae a la del sistema. No afecta a ninguna prueba (todas pasan)
> y no ocurre dentro del contenedor, que corre sobre Linux.

### Colección de Postman

`postman/Biblioteca.postman_collection.json` + `postman/Biblioteca.postman_environment.json`
(38 peticiones en 6 carpetas, con tests automáticos).
Ejecuta **1. Auth → Login (ADMIN)** primero: el token queda guardado y el resto de peticiones
salen autenticadas. La carpeta **6. Escenario completo** reproduce en orden todo el flujo de negocio.

---

## 2. Modelo de datos

```
AppUser                 Book                      Loan                       Reservation
────────                ────────                  ────────                   ────────────
id                      id                        id                         id
name                    title                     book_id  ────────────┐     book_id ─────┐
email (único)           author                    borrower_name        │     requester_email
password_hash (BCrypt)  isbn (único)              borrower_email       │     requested_at
role                    publication_year          loan_date            │     status
blocked_until  ◄──┐     status                    due_date (+14 días)  │     notified_at
blocked_reason    │     cover_url                 return_date          │
                  │     enriched_from_external    reminder_sent_at     │
                  │     book_subjects (1:N)       overdue_notice_sent_at
                  │                                                    │
                  └── se calcula al devolver tarde ◄───────────────────┘
```

Decisiones que vale la pena explicar:

- **Los atrasos no se guardan como contador.** Se derivan de los préstamos
  (`return_date > due_date` dentro de los últimos 90 días). Un contador se desincroniza
  en cuanto alguien corrige un dato a mano; la consulta siempre dice la verdad.
- **`blocked_until` en vez de un booleano `blocked`.** El bloqueo caduca solo: no hace falta
  un job que "desbloquee" cuentas, y siempre se sabe hasta cuándo dura.
- **`@Version` en `Book` y `Loan`** + índice único parcial `uk_loans_active_book`: dos peticiones
  simultáneas no pueden prestar el mismo ejemplar, ni siquiera si pasan la validación a la vez.
- **`book_subjects` como tabla aparte** (`@ElementCollection`) en lugar de una columna con comas:
  los temas vienen de Open Library y son consultables.
- **Índices parciales de PostgreSQL** para las consultas que corre el scheduler todos los días
  (`idx_loans_active_due ... WHERE return_date IS NULL`).

Migraciones en [`backend/src/main/resources/db/migration/`](backend/src/main/resources/db/migration/).
Hibernate corre con `ddl-auto: validate`: el esquema lo manda Flyway, no el ORM.

---

## 3. Reglas de negocio

Implementadas en [`RegistrarPrestamoHandler`](backend/src/main/java/com/jposada/anaquel/application/loan/RegistrarPrestamoHandler.java)
y [`RegistrarLibroHandler`](backend/src/main/java/com/jposada/anaquel/application/book/RegistrarLibroHandler.java).

### Al registrar un préstamo

1. El libro debe estar **DISPONIBLE** → si no, `BookNotAvailableException` (409).
2. La cuenta no puede estar bloqueada → si lo está, `UserBlockedException` (403).
3. Si pasa: el libro va a **PRESTADO**, `dueDate = hoy + 14 días`, y se publica
   `LoanCreatedEvent` → el correo de confirmación sale **después del commit y en otro hilo**.

### Al devolver

1. Si `returnDate > dueDate`, cuenta como **atraso**.
2. Al llegar a **3 atrasos en los últimos 90 días**: la cuenta queda bloqueada **7 días**,
   se guarda el motivo y se dispara el correo de aviso. Un **ADMIN puede levantarlo antes**
   (`POST /api/admin/users/{id}/unblock`).
3. Si hay **lista de espera** para ese título: el libro **no vuelve a DISPONIBLE**, pasa a
   **RESERVADO** y se notifica por correo al primero de la fila.

### Excepciones de negocio

Todas heredan de `BusinessException`, que lleva su código estable y su status HTTP:

| Excepción | Código | HTTP |
|---|---|---|
| `BookNotAvailableException` | `BOOK_NOT_AVAILABLE` | 409 |
| `DuplicateIsbnException` | `DUPLICATE_ISBN` | 409 |
| `UserBlockedException` | `USER_BLOCKED` | 403 |
| `ExternalBookLookupException` | `EXTERNAL_LOOKUP_FAILED` | 503 |
| `ResourceNotFoundException` | `NOT_FOUND` | 404 |
| `EmailAlreadyUsedException` | `EMAIL_ALREADY_USED` | 409 |
| `BusinessRuleException` | varios (`LOAN_ALREADY_RETURNED`, `BOOK_NOT_DELETABLE`, …) | 400/403/409 |

Los parámetros no están quemados en el código: `LOAN_PERIOD_DAYS`, `MAX_LATE_RETURNS`,
`LATE_RETURNS_WINDOW_DAYS`, `BLOCK_DURATION_DAYS` se ajustan por variable de entorno
(útil para demostrar el bloqueo sin esperar 90 días).

---

## 4. Notificaciones por correo

Correo real por SMTP, con MIME HTML renderizado por Thymeleaf desde
[`templates/email/`](backend/src/main/resources/templates/email/). Nada de `System.out.println`.

| # | Correo | Cuándo | Plantilla |
|---|---|---|---|
| 1 | Confirmación de préstamo | Al registrar el préstamo | `loan-confirmation.html` |
| 2 | Recordatorio de vencimiento | Tarea diaria, 1–2 días antes de vencer | `loan-due-soon.html` |
| 3 | Cuenta bloqueada | Al tercer atraso en 90 días | `account-blocked.html` |
| 4 | Libro disponible | Al devolver un libro con lista de espera | `book-available.html` |
| 5 | Préstamo vencido *(extra)* | Tarea diaria, ya pasada la fecha límite | `loan-overdue.html` |

### Por qué no bloquea la petición HTTP

```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onLoanCreated(LoanCreatedEvent event) {
    notificationService.sendLoanConfirmation(event.loanId());
}
```

`AFTER_COMMIT` garantiza que nunca se manda un correo de una transacción que terminó en rollback,
y `@Async` (sobre un pool dedicado, `mailExecutor`) saca el SMTP del hilo de la petición.
Un fallo de correo se registra en el log pero **no tumba el préstamo que ya se registró**.

### Tareas programadas

[`LoanReminderScheduler`](backend/src/main/java/com/jposada/anaquel/infrastructure/scheduling/LoanReminderScheduler.java)

```java
@Scheduled(cron = "${app.scheduling.reminder-cron:0 0 8 * * *}")   // 8:00 — por vencer
@Scheduled(cron = "${app.scheduling.overdue-cron:0 30 8 * * *}")   // 8:30 — ya vencidos
```

`reminderSentAt` y `overdueNoticeSentAt` se marcan **dentro de la misma transacción** que hace
el envío: aunque la tarea se ejecute de nuevo, el mismo aviso no sale dos veces.

**Para verlo sin esperar a las 8 a.m.**: en `.env` pon `REMINDER_CRON=0 * * * * *`
(cada minuto), reinicia el backend y revisa MailHog.

---

## 5. Integración con Open Library

[`OpenLibraryClient`](backend/src/main/java/com/jposada/anaquel/infrastructure/openlibrary/OpenLibraryClient.java)

```java
@Cacheable(cacheNames = "openLibraryLookup", key = "#isbn", unless = "#result.isUnavailable()")
public LookupResult lookupByIsbn(String isbn) { ... }
```

- **Caché Caffeine en memoria**, TTL 24 h, máximo 1 000 entradas. El resultado de un ISBN no cambia.
  Medido en local: primera consulta ≈ 2 s, siguientes **≈ 17 ms**.
- **Los fallos NO se cachean** (`unless = "#result.isUnavailable()"`): un timeout puntual no deja
  ese ISBN "envenenado" durante 24 horas.
- **Nunca propaga excepciones.** Timeout, 5xx, DNS caído o JSON inesperado → `UNAVAILABLE`,
  y el libro se guarda con los datos que escribió la persona.
- **Reintento con backoff** (1 intento extra, 200 ms) solo ante fallos transitorios: timeout, 5xx, 429.
  Un **404 no se reintenta** — ese ISBN simplemente no existe.

### Dos cosas que descubrí probando contra la API real

1. **`GET /isbn/{isbn}.json` responde 302**, redirigiendo a `/books/OL...json`. WebClient no sigue
   redirecciones por defecto, así que la integración siempre fallaba. Se activa con
   `HttpClient.create().followRedirect(true)`.
2. **Muchas ediciones no traen el campo `authors`** — el autor vive en el *work*
   (`/works/OL...json`), y allí con otra forma: `{"author": {"key": "/authors/OL..."}}`.
   El cliente pide el *work* una sola vez y de ahí saca autores **y** temas.

Por eso el timeout por defecto es **5 s y no 3 s**: una consulta son mínimo dos viajes de red
(redirección) y a veces tres (work + autor). Es configurable con `OPENLIBRARY_TIMEOUT_MS`.

### Decisión: 404 y 503 no son lo mismo

`GET /api/books/lookup/{isbn}` distingue tres casos, porque para el usuario significan cosas distintas:

| Situación | Respuesta | Lo que ve el usuario |
|---|---|---|
| El ISBN existe en Open Library | `200` con la previsualización | El formulario se llena solo |
| El ISBN no existe allá | `404 NOT_FOUND` | "No encontramos el ISBN, escríbelo a mano" |
| La API no respondió | `503 EXTERNAL_LOOKUP_FAILED` | "Open Library no respondió, el libro se guarda igual" |

El endpoint **no guarda nada**: es solo la previsualización que alimenta el botón
"Autocompletar desde ISBN" del formulario.

**Al registrar el libro** (`POST /api/books`) el comportamiento es distinto y deliberado:
si la API falla, **no se propaga ningún error**. Se usa lo que escribió la persona y el libro
se guarda con `enrichedFromExternal = false`. Solo si además faltan título y autor se responde
`400 MISSING_BOOK_DATA` pidiéndolos — nunca un 500.

---

## 6. API REST y seguridad

### Endpoints

| Método | Ruta | Acceso |
|---|---|---|
| `POST` | `/api/auth/register` | Público |
| `POST` | `/api/auth/login` | Público |
| `GET` | `/api/auth/me` | Autenticado |
| `GET` | `/api/books` | Autenticado — `?search=` `?status=` `?page=` `?size=` |
| `GET` | `/api/books/{id}` | Autenticado |
| `GET` | `/api/books/lookup/{isbn}` | Autenticado — previsualización, no guarda nada |
| `POST` | `/api/books` | **ADMIN** |
| `DELETE` | `/api/books/{id}` | **ADMIN** — solo si está DISPONIBLE |
| `POST` | `/api/loans` | Autenticado |
| `GET` | `/api/loans/mine` | Autenticado |
| `GET` | `/api/loans` | **ADMIN** |
| `PUT` | `/api/loans/{id}/return` | Dueño del préstamo o ADMIN |
| `POST` | `/api/reservations` | Autenticado |
| `POST` | `/api/reservations/{id}/confirm` | Dueño de la reserva o ADMIN — **crea el préstamo en un paso** |
| `GET` | `/api/reservations/mine` | Autenticado |
| `DELETE` | `/api/reservations/{id}` | Dueño de la reserva o ADMIN |
| `GET` | `/api/admin/stats` | **ADMIN** |
| `GET` | `/api/admin/users` · `/users/blocked` | **ADMIN** |
| `POST` | `/api/admin/users/{id}/unblock` | **ADMIN** |

Documentación completa y probable en **Swagger UI**: http://localhost:8080/swagger-ui.html
(botón *Authorize* → pega el token del login).

### Errores consistentes

Un único `@RestControllerAdvice`
([`GlobalExceptionHandler`](backend/src/main/java/com/jposada/anaquel/web/GlobalExceptionHandler.java))
devuelve siempre la misma forma — incluidos los 401 y 403 que Spring Security genera antes
de llegar al controlador:

```json
{
  "timestamp": "2026-08-18T00:56:54.974Z",
  "status": 409,
  "code": "BOOK_NOT_AVAILABLE",
  "message": "El libro '1984' no esta disponible para prestamo (estado actual: PRESTADO).",
  "path": "/api/loans"
}
```

Los errores de validación añaden el detalle campo por campo:

```json
{
  "status": 400,
  "code": "VALIDATION_ERROR",
  "errors": [{ "field": "isbn", "message": "El ISBN es obligatorio" }]
}
```

### Seguridad

- **JWT** firmado con HMAC-SHA (jjwt 0.12.6), sin estado en el servidor.
- **BCrypt** siempre: la contraseña en claro nunca se guarda ni se registra en logs.
- Autorización en dos capas: reglas por ruta en `SecurityConfig` **y** `@PreAuthorize` en los
  controladores. Si alguien mueve una ruta, la anotación sigue protegiendo el método.
- El servidor no revela si un correo existe: contraseña incorrecta y correo inexistente
  devuelven el mismo `401 BAD_CREDENTIALS`.
- CSRF desactivado porque la API es *stateless* con JWT (no hay sesión con cookie que proteger).

---

## 7. Frontend

Angular 20 + TypeScript: componentes **standalone**, estado con **signals** (sesión y avisos en
servicios `providedIn: 'root'`), interceptores y guards **funcionales**, rutas con carga perezosa.
Plantillas y estilos siempre en archivos externos (`npm run lint:no-inline` lo vigila).

| Pantalla | Qué hace |
|---|---|
| **Login / Registro** | Validación en el cliente con las mismas reglas del backend |
| **Catálogo** | Búsqueda por título/autor/ISBN (con *debounce*), filtro por estado, paginación, prestar, entrar a la lista de espera y —para ADMIN— registrar/eliminar libros |
| **Registrar libro** | Botón **"Autocompletar desde ISBN"**: llama al *preview*, llena el formulario y sigue siendo editable a mano. En cuanto el ISBN escrito es válido la consulta se dispara sola; si no tienes uno a mano, el formulario ofrece tres ISBN reales clicables |
| **Mis préstamos** | Préstamos activos con aviso de vencido y días de atraso, devolución, lista de espera con la posición en la fila, e historial |
| **Panel de administración** | Estadísticas (prestados, vencidos, por vencer, bloqueadas), cuentas bloqueadas con botón de levantar bloqueo, y todos los préstamos activos |

Detalles que importan:

- **Un solo punto de salida HTTP**: el [`authInterceptor`](frontend/src/app/core/auth/interceptors/auth.interceptor.ts)
  agrega el token en cada llamada y el [`errorInterceptor`](frontend/src/app/core/auth/interceptors/error.interceptor.ts)
  normaliza los errores del backend en una clase `ApiError` que **conserva el código de negocio**
  (`USER_BLOCKED`, `DUPLICATE_ISBN`…) y las violaciones campo a campo, y cierra la sesión
  automáticamente ante cualquier 401.
- **Nada de tragarse un error en silencio**: cada pantalla tiene sus tres estados —cargando, error
  con el mensaje real del backend y botón de reintentar, y vacío—. Los errores de acción se muestran
  como avisos con el texto que mandó el servidor.
- Las peticiones se **cancelan** al cambiar de filtro o destruir el componente
  ([`Cargable`](frontend/src/app/core/http/cargable.ts): datos / cargando / error como signals,
  con la petición anterior anulada al recargar).
- Tras devolver un libro se refresca el usuario: la devolución tardía pudo haber bloqueado la cuenta.
- **Movimiento con GSAP** (directivas `appReveal` y `appCountUp`, más `afterNextRender` en los
  componentes): la barra lateral y los libros entran escalonados, el filtro de estado desliza su
  indicador, los diálogos entran y salen con la misma línea de tiempo, los avisos se deslizan y
  muestran cuánto les queda, y las estadísticas cuentan hasta su valor. Todo respeta
  `prefers-reduced-motion`. Las entradas usan `fromTo` con valores finales explícitos (helper
  `aparecer` en [`core/motion/motion.ts`](frontend/src/app/core/motion/motion.ts)) porque
  `gsap.from` lee el estado final con `getComputedStyle` y, en elementos con transición CSS, puede
  leer un valor a medio camino.
- **Signals y servicios en vez de NgRx**: para cuatro pantallas, un `AuthStore` con signals y
  `localStorage` (30 líneas) hace lo mismo que un store con acciones, reducers y effects. La
  prueba aceptaba explícitamente "Signals/Services en Angular".
- **Portadas reales, gratis y sin clave**: Open Library las sirve por ISBN
  (`covers.openlibrary.org/b/isbn/{isbn}-L.jpg`). El backend guarda la URL al dar de alta por ISBN
  y la semilla trae portada para 4 de los 5 libros (`V3__seed_covers.sql`); si el backend no tiene
  URL, el componente `app-cover` la intenta igualmente por ISBN con `?default=false` (404 si no
  existe) y, si tampoco, muestra la inicial del título. Nada de imágenes aleatorias: una portada
  que no es la del libro confunde más que una letra.
- **Nada de `window.confirm`**: quitar un libro pide confirmación en un diálogo propio, con la
  misma identidad visual y cierre por Escape.

---

## 8. Pruebas

```
107 pruebas · 0 fallos
```

| Archivo | Qué cubre |
|---|---|
| `PrestamosHandlerTest` (14) | Préstamo, devolución, 3 atrasos → bloqueo, lista de espera, permisos, eventos publicados |
| `RegistrarLibroHandlerTest` (12) | ISBN duplicado, normalización, autocompletado, prioridad de los datos manuales, degradación cuando la API falla |
| `OpenLibraryClientTest` (10) | Mapeo completo, **redirección 302**, autor desde el *work*, 404, 500, timeout, JSON inválido, sin portada |
| `IsbnUtilsTest` (10) | Normalización de ISBN |
| `BookControllerIT` (13) | MockMvc: 401 sin token, roles, ISBN duplicado, validaciones, formato de error |
| `AuthControllerIT` (5) | Registro, login, **hash BCrypt en base**, la contraseña nunca sale en la respuesta |
| `ReservationQueueIT` (9) | La fila siempre avanza: salta cuentas bloqueadas, caduca los turnos abandonados y confirma el préstamo en un paso |
| `LoanFlowIT` (9) | Flujo end-to-end contra base real: prestar → reservar → devolver → bloqueo → schedulers |
| `NotificationServiceIT` (5) | **GreenMail**: el correo sale de verdad, con asunto, destinatario y contenido correctos |
| `OpenApiSpecIT` (1) | La documentación OpenAPI está completa (rutas, errores, seguridad) y se exporta a `docs/openapi.json` |

- **GreenMail** levanta un SMTP embebido y verifica los mensajes reales, no mocks del `JavaMailSender`.
- **MockWebServer** simula Open Library, incluidos los caminos de fallo (timeout, 5xx, JSON roto).
- Las pruebas corren sobre **H2** con `ddl-auto: create-drop`: Flyway se desactiva en el perfil de
  test porque las migraciones son SQL específico de PostgreSQL (índices parciales, `TIMESTAMPTZ`).
  El esquema de test sale de las mismas entidades JPA que valida Hibernate contra el esquema real.

---

## 9. Variables de entorno

Todas tienen valor por defecto para desarrollo local. Ver [`.env.example`](.env.example).

| Variable | Por defecto | Para qué |
|---|---|---|
| `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | `anaquel` | PostgreSQL (`DB_URL` la compone Docker Compose; sin Docker, `jdbc:postgresql://localhost:5432/anaquel`) |
| `DB_PORT` / `API_PORT` / `WEB_PORT` | `5432` / `8080` / `5173` | Puertos publicados por Docker Compose |
| `JWT_SECRET` | *(dev)* | Firma del token. **Mínimo 32 bytes**; la app no arranca si es más corto |
| `JWT_EXPIRATION_MINUTES` | `480` | Vigencia del token |
| `MAIL_HOST` / `MAIL_PORT` | `mailhog` / `1025` | SMTP |
| `MAIL_FROM` | `hola@anaquel.app` | Remitente |
| `APP_PUBLIC_URL` | `http://localhost:5173` | Enlaces dentro de los correos |
| `API_PUBLIC_URL` | `http://localhost:8080` | URL base declarada en el spec de OpenAPI |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,…` | Orígenes permitidos |
| `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD` | `admin@anaquel.app` / `Admin123*` | Cuenta ADMIN de prueba |
| `SEED_LIBRARIAN_EMAIL` / `SEED_LIBRARIAN_PASSWORD` | `lectura@anaquel.app` / `Admin123*` | Cuenta BIBLIOTECARIO de prueba |
| `LOAN_PERIOD_DAYS` | `14` | Plazo del préstamo |
| `MAX_LATE_RETURNS` | `3` | Atrasos que disparan el bloqueo |
| `LATE_RETURNS_WINDOW_DAYS` | `90` | Ventana en que se cuentan los atrasos |
| `BLOCK_DURATION_DAYS` | `7` | Duración del bloqueo |
| `REMINDER_DAYS_BEFORE` | `2` | Días de anticipación del recordatorio |
| `RESERVATION_HOLD_HOURS` | `48` | Horas que se guarda el libro a quien tiene el turno |
| `EXPIRE_HOLDS_CRON` | `0 5 * * * *` | Tarea que libera los turnos no confirmados |
| `REMINDER_CRON` / `OVERDUE_CRON` | `0 0 8 * * *` / `0 30 8 * * *` | Tareas programadas |
| `OPENLIBRARY_TIMEOUT_MS` | `5000` | Timeout por llamada a la API externa |
| `OPENLIBRARY_MAX_RETRIES` | `1` | Reintentos ante fallos transitorios |
| `OPENLIBRARY_CACHE_TTL_HOURS` | `24` | Vigencia de la caché |

### Reglas que se endurecieron en la revisión final

- El registro público **ignora cualquier `role` que llegue en el cuerpo**: siempre crea
  `BIBLIOTECARIO`. Los ADMIN salen únicamente del seeding.
- El **ISBN se valida con dígito de control** (módulo 11 para ISBN-10, módulo 10 para ISBN-13),
  tanto en el alta como en la previsualización → `400 INVALID_ISBN`. El formulario del frontend
  aplica la misma regla mientras escribes.
- No se puede **reservar el libro que uno mismo tiene prestado** (`409 ALREADY_BORROWED`): sería
  una renovación encubierta que saltaría a todos los demás de la fila.
- Una **cuenta bloqueada no entra en la lista de espera** (`403 USER_BLOCKED`), igual que no puede
  pedir préstamos.
- Un **turno vencido no se puede confirmar** aunque el cron horario aún no lo haya caducado
  (`409 RESERVATION_EXPIRED`).
- Si un ADMIN confirma la reserva de otra persona, el préstamo queda **a nombre de esa persona**.
- Un turno caducado sobre un libro que ya se volvió a prestar **no lo pone `DISPONIBLE`**: la
  cola comprueba que no exista un préstamo activo antes de tocar el estado del libro.
- El scheduler marca `reminderSentAt` / `overdueNoticeSentAt` **solo si el SMTP aceptó el
  mensaje**: si el correo estaba caído a las 8:00, se reintenta al día siguiente.
- Una ruta inexistente responde `404 NOT_FOUND` con el mismo JSON de error (no un 500).

### Sobre los secretos

**No hay ninguna credencial real en el repositorio.** `.env` está en `.gitignore`; lo versionado
es `.env.example`, una plantilla con valores de desarrollo. Las contraseñas de las cuentas de
prueba **no están en ninguna migración SQL**: las crea `DataSeeder` a partir de una variable de
entorno, y la aplicación **escribe una advertencia en el log** si arranca con la contraseña por
defecto. En un entorno real, `JWT_SECRET` se genera con `openssl rand -base64 48`.

---

## 10. Cómo está organizado el backend

El código no se agrupa por capa técnica sino **por funcionalidad**, y dentro de cada una se
separa lo que **cambia estado** de lo que **solo lee** (CQRS):

```
web/            traduce HTTP a una intención. No decide nada.
    ↓
application/    un record por intención + un handler por caso de uso.
    ↓           AQUÍ ESTÁN LAS REGLAS DE NEGOCIO.
domain/         entidades con comportamiento y servicios de dominio.
    ↓
infrastructure/ los adaptadores: JPA, Open Library, correo, JWT, cron.
```

Buscar dónde vive una regla deja de ser un rastreo entre cuatro paquetes:

| Quiero entender… | Abro |
|---|---|
| Qué pasa al prestar un libro | `application/loan/RegistrarPrestamoHandler.java` |
| La regla de los 3 atrasos | `application/loan/DevolverPrestamoHandler.java` |
| A quién le toca el libro devuelto | `domain/reservation/ReservationQueue.java` |
| El autocompletado por ISBN | `application/book/query/PrevisualizarIsbnHandler.java` |

**Los comandos son `record`** porque son datos inmutables de entrada, sin comportamiento.
**Las entidades son clases** porque tienen identidad, estado y reglas propias
(`loan.isReturnedLate()`, `user.block(...)`).

**No hay bus de comandos, y es deliberado.** La inyección de dependencias de Spring ya resuelve
el handler por tipo en tiempo de compilación; un bus con `Map<Class, Handler>` solo añadiría
indirección y stacktraces peores. Un bus se justifica cuando hace falta interceptar cosas
transversales (auditoría, reintentos, encolado), y aquí no era el caso.

---

## 11. Decisiones que tomé y por qué

Siguiendo la indicación de documentar en vez de adivinar:

1. **Un libro RESERVADO solo se lo puede llevar quien fue notificado.**
   El enunciado dice que el libro pasa a RESERVADO, pero no qué pasa después. Si cualquiera
   pudiera tomarlo, la lista de espera no serviría de nada. Si nadie pudiera, el libro quedaría
   atrapado. Solución: quien tiene la reserva `NOTIFICADO` puede confirmar el préstamo (su reserva
   pasa a `CUMPLIDO`); para el resto sigue no disponible. Al **cancelar** esa reserva, el turno
   pasa automáticamente al siguiente de la fila, y si no queda nadie el libro vuelve a DISPONIBLE.

2. **El bloqueo impide pedir préstamos, no iniciar sesión.**
   El enunciado dice "bloqueada para pedir nuevos préstamos". Una cuenta bloqueada sigue pudiendo
   entrar, consultar el catálogo y **devolver** lo que tiene — si no pudiera devolver, nunca
   saldría del bloqueo. Por eso `AppUserPrincipal.isAccountNonLocked()` devuelve `true` y la
   validación vive en `RegistrarPrestamoHandler`, que es donde corresponde.

3. **Un BIBLIOTECARIO solo se presta a sí mismo; un ADMIN puede prestar a nombre de otros.**
   El enunciado no lo define. Sin esta regla, cualquiera podría registrar préstamos a nombre
   ajeno y llenar de atrasos una cuenta que no es suya.

4. **El bloqueo no se reinicia si ya estaba bloqueado.**
   Si una cuenta bloqueada devuelve un cuarto libro tarde, no se le suma otra semana ni se le
   manda otro correo. Se sanciona el hecho de llegar al umbral, no cada devolución posterior.

5. **El turno de la lista de espera caduca, salta a las cuentas bloqueadas y se confirma en un clic.**
   El enunciado dice que el libro pasa a RESERVADO, pero no qué ocurre después, y ahí había tres
   agujeros: (a) si el primero de la fila estaba bloqueado por atrasos, el libro quedaba
   **atrapado** —él no podía tomarlo y nadie más tampoco—; (b) la reserva `NOTIFICADO` no caducaba
   nunca, así que quien no aparecía dejaba el libro fuera de circulación para siempre; y (c) había
   que ir al catálogo a buscar el libro para pedirlo, lo que hacía que el préstamo *pareciera*
   demorado. Ahora `ReservationQueue` recorre la fila y le da el turno al primero que **realmente
   pueda** tomarlo (a los bloqueados se les salta sin perder el puesto), una tarea horaria libera
   los turnos que pasan de `RESERVATION_HOLD_HOURS`, y `POST /api/reservations/{id}/confirm` crea
   el préstamo en un solo paso. Todo ello sin romper el requisito del enunciado: el libro sigue
   pasando a RESERVADO.

6. **`GET /api/books/lookup/{isbn}` distingue 404 de 503.** Explicado en la sección 5:
   "el ISBN no existe" y "el servicio se cayó" piden acciones distintas del usuario.

7. **Registrarse crea siempre un BIBLIOTECARIO.** El endpoint no acepta ningún campo `role`
   (si llega, se ignora): sin un flujo de invitaciones, aceptarlo dejaría que cualquiera se
   auto-asignara ADMIN. Los ADMIN reales salen del seeding controlado por variables de entorno.

8. **Eliminar un libro exige que esté DISPONIBLE** *y* que no tenga préstamos activos.
   La doble comprobación cubre el caso de un estado desincronizado por una corrección manual.

9. **Flyway se desactiva en las pruebas.** Las migraciones usan SQL propio de PostgreSQL
   (índices parciales `WHERE`, `TIMESTAMPTZ`, `GENERATED BY DEFAULT AS IDENTITY`). Escribirlas en
   SQL portable habría empobrecido el esquema real solo para complacer a H2. Las pruebas generan
   el esquema desde las entidades; que ambos coincidan lo garantiza `ddl-auto: validate` al
   arrancar contra Postgres.

10. **El timeout de Open Library es 5 s y no los 3 s del enunciado.** Medido contra la API real:
   la redirección 302 hace que una consulta sean mínimo dos viajes de red. Con 3 s el camino feliz
   fallaba de forma intermitente. Es configurable.

11. **Un correo que falla no revierte la operación de negocio.** `NotificationService` registra
    el fallo en el log y no lo propaga: un préstamo válido no se pierde porque el SMTP esté caído.

---

## 12. Estructura

```
.
├── docker-compose.yml            app + Postgres + MailHog + frontend
├── .env.example                  plantilla de variables (sin secretos reales)
├── postman/                      colección + environment
├── backend/
│   ├── Dockerfile                build multi-etapa, corre como usuario sin privilegios
│   └── src/main/
│       ├── java/com/jposada/anaquel/
│       │   ├── domain/           el negocio, sin saber que existe HTTP ni JPA
│       │   │   ├── book/ loan/ user/ reservation/   entidades con comportamiento
│       │   │   └── shared/       excepciones de negocio y eventos de dominio
│       │   ├── application/      UN ARCHIVO POR CASO DE USO
│       │   │   ├── loan/         RegistrarPrestamo · DevolverPrestamo + query/
│       │   │   ├── book/         RegistrarLibro · EliminarLibro + query/
│       │   │   ├── reservation/  EntrarEnListaDeEspera · ConfirmarReserva · CancelarReserva
│       │   │   ├── account/      CrearCuenta · IniciarSesion
│       │   │   ├── admin/        LevantarBloqueo + query/
│       │   │   └── shared/       Command · Query · UseCase
│       │   ├── infrastructure/   los adaptadores al mundo exterior
│       │   │   ├── persistence/  repositorios Spring Data
│       │   │   ├── openlibrary/  cliente de la API externa + caché
│       │   │   ├── mail/         envío de correo y listener asíncrono
│       │   │   ├── security/     JWT, filtro y manejadores 401/403
│       │   │   ├── scheduling/   tareas diarias y caducidad de turnos
│       │   │   └── config/       Security, Async, Cache, WebClient, OpenAPI
│       │   └── web/              controladores, DTOs y @RestControllerAdvice
│       └── resources/
│           ├── db/migration/     V1 esquema · V2 catálogo de ejemplo · V3 portadas
│           └── templates/email/  5 plantillas Thymeleaf
└── frontend/
    ├── Dockerfile + nginx.conf   build estático servido por nginx, /api proxeado al backend
    ├── proxy.conf.json           ng serve: /api → localhost:8080
    ├── scripts/lint-no-inline    guardia: nada de plantillas/estilos inline
    └── src/app/
        ├── core/
        │   ├── auth/             modelos · AuthStore (signals) · guards · interceptores
        │   ├── http/             ApiError · Cargable (cargando/error/dato + cancelación)
        │   ├── services/         books · loans · reservations · admin · toast
        │   └── motion/           GSAP: helper `aparecer` + directivas appReveal / appCountUp
        ├── shared/               icon, chip, note, estados, cover, segmented, sheet, confirm, toasts, stat · utils · validators
        ├── layouts/shell/        barra lateral + <router-outlet>
        └── features/             auth/login · catalog (+ alta de libro) · loans · admin
```
