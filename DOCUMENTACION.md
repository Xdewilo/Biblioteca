# Documentación técnica — Anaquel

> **Para qué sirve este documento.** El [README](README.md) explica *cómo levantar* el proyecto.
> Este explica *cómo está hecho por dentro y por qué*: qué tecnología se usó en cada parte,
> qué hace cada archivo, y cómo viaja una petición desde el navegador hasta la base de datos y
> de vuelta. Está escrito para poder defender cualquier decisión en una entrevista técnica.
>
> Léelo en orden la primera vez. Después úsalo como referencia por secciones.
>
> Si lo que necesitas es **explicar la API en voz alta** frente a Swagger UI, hay un documento
> aparte con el guion de la demo: [`GUIA-API.md`](GUIA-API.md).

---

## Índice

1. [Resumen en una página](#1-resumen-en-una-página)
2. [El stack: qué es cada cosa y por qué está aquí](#2-el-stack-qué-es-cada-cosa-y-por-qué-está-aquí)
3. [Mapa del proyecto](#3-mapa-del-proyecto)
4. [Cómo se construyó, paso a paso](#4-cómo-se-construyó-paso-a-paso)
5. [El backend capa por capa](#5-el-backend-capa-por-capa)
6. [Los tres flujos de negocio, trazados](#6-los-tres-flujos-de-negocio-trazados)
7. [Integración con Open Library en detalle](#7-integración-con-open-library-en-detalle)
8. [El sistema de correo en detalle](#8-el-sistema-de-correo-en-detalle)
9. [Seguridad en detalle](#9-seguridad-en-detalle)
10. [El frontend en detalle](#10-el-frontend-en-detalle)
11. [Las pruebas, explicadas](#11-las-pruebas-explicadas)
12. [Docker, explicado](#12-docker-explicado)
13. [Glosario de anotaciones y conceptos](#13-glosario-de-anotaciones-y-conceptos)
14. [Preguntas que te pueden hacer y cómo responderlas](#14-preguntas-que-te-pueden-hacer-y-cómo-responderlas)

---

## 1. Resumen en una página

**El problema.** Una biblioteca lleva los préstamos en un Excel que vive en un solo computador.
Nadie sabe si un libro está disponible sin llamar, los atrasos no se avisan, y quien quiere un
libro prestado tiene que preguntar cada semana.

**La solución.** Una aplicación web con tres piezas que se hablan entre sí:

```
   NAVEGADOR                    SERVIDOR                        AFUERA
┌──────────────┐         ┌────────────────────┐         ┌──────────────────┐
│              │  HTTP   │                    │  HTTP   │  Open Library    │
│ Angular + TS │ ──────► │   Spring Boot      │ ──────► │  (datos del      │
│              │  +JWT   │                    │         │   libro por ISBN)│
│  4 pantallas │ ◄────── │  reglas de negocio │ ◄────── │                  │
└──────────────┘  JSON   │                    │         └──────────────────┘
                         │                    │  SMTP   ┌──────────────────┐
                         │                    │ ──────► │  MailHog         │
                         │                    │         │  (correos)       │
                         └─────────┬──────────┘         └──────────────────┘
                                   │ JDBC
                                   ▼
                         ┌────────────────────┐
                         │    PostgreSQL      │
                         └────────────────────┘
```

**En números:**

| | |
|---|---|
| Java (código) | 73 archivos · 3 342 líneas |
| Java (pruebas) | 9 archivos · 1 640 líneas · **107 pruebas** |
| TypeScript | 18 archivos · 1 707 líneas |
| SQL (migraciones) | 2 archivos · 121 líneas |
| Plantillas de correo | 6 archivos HTML |
| Endpoints REST | 16 |

**Las tres reglas de negocio que dan valor** (y que separan esto de un CRUD):

1. Al prestar: el libro debe estar disponible y la cuenta no bloqueada → 14 días de plazo + correo.
2. Al devolver tarde 3 veces en 90 días: la cuenta se bloquea una semana + correo (un ADMIN lo levanta).
3. Al devolver un libro que alguien esperaba: **no vuelve a disponible**, queda reservado para el
   primero de la fila y se le avisa.

---

## 2. El stack: qué es cada cosa y por qué está aquí

### Backend

| Tecnología | Qué es | Por qué está aquí | Dónde se ve |
|---|---|---|---|
| **Java 21** | El lenguaje | Lo pedía la prueba. Se aprovechan `record`, `switch` con patrones y bloques de texto | Todo el backend |
| **Spring Boot 3.3.5** | Framework que arranca la app y conecta las piezas | Estándar de la industria en Java. Da inyección de dependencias, servidor web embebido y autoconfiguración | `BibliotecaApplication.java` |
| **Spring Web MVC** | Expone HTTP | Convierte clases Java en endpoints REST | `web/controller/` |
| **Spring Data JPA** | Habla con la base de datos | Se escribe una interfaz y Spring genera el SQL | `infrastructure/persistence/` |
| **Hibernate** | El ORM debajo de JPA | Mapea objetos Java ↔ filas de tabla | Anotaciones `@Entity` en `domain/` |
| **Bean Validation** | Valida los datos que entran | `@NotBlank`, `@Email`… antes de tocar la lógica | `web/dto/` |
| **Spring Security** | Autenticación y permisos | Protege los endpoints por rol | `infrastructure/config/SecurityConfig.java` |
| **JJWT 0.12.6** | Genera y verifica tokens JWT | Login sin estado en el servidor | `infrastructure/security/JwtService.java` |
| **BCrypt** | Hashea contraseñas | Una contraseña nunca se guarda legible | `SecurityConfig.passwordEncoder()` |
| **Spring Mail** | Envía correo por SMTP | Correo real, no simulado | `infrastructure/mail/` |
| **Thymeleaf** | Motor de plantillas HTML | Arma el cuerpo del correo sin concatenar strings | `templates/email/` |
| **Caffeine** | Caché en memoria | Evita golpear Open Library con el mismo ISBN | `infrastructure/config/CacheConfig.java` |
| **WebClient** | Cliente HTTP moderno | Consulta la API externa con timeout y reintentos | `infrastructure/config/WebClientConfig.java` |
| **PostgreSQL 16** | Base de datos | Lo pedía la prueba. Da índices parciales, que aquí se usan | `docker-compose.yml` |
| **Flyway** | Versiona el esquema de la BD | El esquema es código versionado, no algo que alguien tocó a mano | `db/migration/` |
| **springdoc-openapi** | Genera Swagger UI | Documentación de la API que nunca se desactualiza | `infrastructure/config/OpenApiConfig.java` |
| **Lombok** | Quita el código repetitivo | `@Getter`, `@Builder`… para no escribir 40 getters | Entidades |
| **Maven** | Construye el proyecto | Descarga dependencias, compila, prueba y empaqueta | `pom.xml` |

### Frontend

| Tecnología | Qué es | Por qué está aquí |
|---|---|---|
| **Angular 20** | Framework de interfaz | La prueba dejaba elegir; componentes standalone, signals, `inject()`, control flow `@if/@for` |
| **TypeScript 5.9** | JavaScript con tipos | La prueba lo exigía. Si el backend cambia un campo, el compilador avisa (`strictTemplates` incluido) |
| **Angular CLI (esbuild)** | Servidor de desarrollo y empaquetador | `ng serve` con proxy `/api` → backend; build de producción con presupuestos de tamaño |
| **Signals + servicios** | Manejo de estado global | Sesión (`AuthStore`) y avisos (`ToastService`) en servicios `providedIn: 'root'`. Sin NgRx: para 4 pantallas sería ceremonia |
| **Angular Router** | Enrutado entre pantallas | Rutas con carga perezosa, guards funcionales por sesión y por rol |
| **Reactive Forms** | Formularios | Validadores que reflejan las reglas del backend (correo, 8 caracteres, dígito de control del ISBN) |
| **SCSS propio** | Estilos | Sin framework de UI: se ve el criterio de diseño y no se infla el bundle |
| **GSAP** | Animación | Directivas `appReveal` / `appCountUp` y `afterNextRender`: entradas escalonadas, filtro con indicador deslizante, diálogos con salida animada, avisos con barra de tiempo y contadores. Todo consulta `prefers-reduced-motion` y usa `fromTo` con valores finales explícitos (ver `aparecer` en `core/motion/motion.ts`) |
| **Jasmine + Karma** | Pruebas unitarias | 36 pruebas: store, interceptores, guards, `Cargable`, avisos, login y alta de libro (con `HttpTestingController`) |

### Pruebas e infraestructura

| Tecnología | Qué es | Por qué está aquí |
|---|---|---|
| **JUnit 5** | Motor de pruebas | Estándar en Java |
| **Mockito** | Crea dobles de prueba | Prueba la lógica sin base de datos ni red |
| **AssertJ** | Aserciones legibles | `assertThat(x).isEqualTo(y)` se lee como una frase |
| **MockMvc** | Simula peticiones HTTP | Prueba los endpoints sin levantar un servidor real |
| **GreenMail** | Servidor SMTP embebido | Verifica que el correo **sale de verdad**, no que se llamó a un método |
| **MockWebServer** | Servidor HTTP falso | Simula Open Library, incluidos sus fallos |
| **H2** | Base de datos en memoria | Las pruebas corren sin Docker |
| **Docker Compose** | Orquesta contenedores | Un comando levanta app + BD + correo + web |
| **MailHog** | SMTP falso con interfaz web | Ver los correos enviados sin credenciales reales |
| **nginx** | Servidor web | Sirve el frontend compilado y hace de proxy hacia la API |

---

## 3. Mapa del proyecto

```
FullStack/
│
├── README.md                    Cómo levantarlo + decisiones tomadas
├── DOCUMENTACION.md             Este archivo
├── docker-compose.yml           Orquesta los 4 servicios
├── .env.example                 Plantilla de variables (SIN secretos reales)
├── .gitignore                   .env queda fuera del repositorio
│
├── postman/                     Colección de 38 peticiones con tests
│
├── backend/
│   ├── pom.xml                  Dependencias y build de Maven
│   ├── Dockerfile               Build en 2 etapas, corre sin privilegios
│   └── src/
│       ├── main/
│       │   ├── java/com/jposada/anaquel/
│       │   │   ├── AnaquelApplication.java      punto de entrada
│       │   │   ├── domain/       EL NEGOCIO, sin saber que existe HTTP ni JPA
│       │   │   │   ├── book/ loan/ user/        entidades con comportamiento
│       │   │   │   ├── reservation/             + ReservationQueue (servicio de dominio)
│       │   │   │   └── shared/                  excepciones y eventos de dominio
│       │   │   ├── application/  UN ARCHIVO POR CASO DE USO (39)
│       │   │   │   ├── loan/         RegistrarPrestamo · DevolverPrestamo + query/
│       │   │   │   ├── book/         RegistrarLibro · EliminarLibro + query/
│       │   │   │   ├── reservation/  EntrarEnListaDeEspera · ConfirmarReserva · Cancelar
│       │   │   │   ├── account/      CrearCuenta · IniciarSesion
│       │   │   │   ├── admin/        LevantarBloqueo + query/
│       │   │   │   └── shared/       Command · Query · UseCase
│       │   │   ├── infrastructure/  LOS ADAPTADORES
│       │   │   │   ├── persistence/  repositorios Spring Data
│       │   │   │   ├── openlibrary/  cliente externo + caché
│       │   │   │   ├── mail/         envío y listener asíncrono
│       │   │   │   ├── security/     JWT y permisos
│       │   │   │   ├── scheduling/   tareas diarias
│       │   │   │   └── config/       la configuración de Spring
│       │   │   └── web/          controladores, DTOs y manejo de errores
│       │   └── resources/
│       │       ├── application.yml         toda la configuración
│       │       ├── db/migration/           V1 esquema · V2 catálogo de ejemplo · V3 portadas
│       │       └── templates/email/        5 plantillas + 1 de fragmentos
│       └── test/                (10 archivos, 107 pruebas)
│
└── frontend/
    ├── package.json             Dependencias de Node
    ├── angular.json             Puerto 5173, proxy y presupuestos de tamaño
    ├── proxy.conf.json          Proxy /api → localhost:8080 en desarrollo
    ├── Dockerfile + nginx.conf  Build estático servido por nginx
    ├── scripts/lint-no-inline   Guardia: plantillas y estilos siempre en archivos externos
    └── src/
        ├── main.ts              bootstrapApplication
        ├── environments/        apiUrl vacío: rutas relativas en dev y en Docker
        └── app/
            ├── app.config.ts    Router + HttpClient con interceptores
            ├── app.routes.ts    Rutas perezosas con guards
            ├── core/            auth (modelos, AuthStore, guards, interceptores) · http (ApiError, Cargable) · services · motion (GSAP)
            ├── shared/          Componentes de UI (icon, chip, note, estados, cover, segmented, sheet, confirm, toasts, stat), utils y validators
            ├── layouts/shell/   Barra lateral + router-outlet
            └── features/        auth/login · catalog (+ alta de libro) · loans · admin
```

**La regla mental para orientarte:** las dependencias apuntan hacia adentro.

```
web/  ──►  application/  ──►  domain/  ◄──  infrastructure/
```

`web/` traduce HTTP a una intención y no decide nada. `application/` tiene un archivo por caso
de uso: ahí están las reglas. `domain/` no sabe que existe HTTP, JPA ni el correo — es el único
paquete que se podría copiar a otro proyecto tal cual.

**Comandos vs consultas (CQRS):** lo que cambia estado implementa `Command`; lo que solo lee,
`Query`. Las consultas van con `@Transactional(readOnly = true)` y se pueden optimizar sin
miedo a romper una regla de negocio.

---

## 4. Cómo se construyó, paso a paso

Este fue el orden real de construcción. Sirve para explicar el razonamiento.

**Paso 1 — Leer el enunciado y verificar herramientas.**
Antes de escribir nada: confirmar que estaban Java 21, Maven, Node, Docker y Git.

**Paso 2 — El modelo de datos primero.**
Las 4 entidades (`Book`, `Loan`, `AppUser`, `Reservation`) y sus 3 enums. Se empieza por aquí
porque el modelo condiciona todo lo demás: si el modelo está mal, la lógica se retuerce.

**Paso 3 — Los repositorios.**
Interfaces de Spring Data con las consultas que la lógica va a necesitar
(atrasos de los últimos 90 días, préstamos por vencer, cola de espera de un libro).

**Paso 4 — Las excepciones de negocio.**
Antes de la lógica, porque la lógica las va a lanzar. Cada una lleva su código estable
(`BOOK_NOT_AVAILABLE`) y su status HTTP, para que el manejo de errores sea automático.

**Paso 5 — Los DTOs.**
Objetos de entrada y salida separados de las entidades. Así la API no expone el `passwordHash`
por accidente y se puede cambiar la base sin romper el contrato REST.

**Paso 6 — Seguridad.**
JWT, filtro, `UserDetails`, y los manejadores de 401/403 para que esos errores también salgan
con el mismo formato JSON.

**Paso 7 — Configuración.**
Security, Async, Cache, WebClient, OpenAPI, y las propiedades de negocio configurables.

**Paso 8 — Open Library.**
El cliente externo, con su caché y —sobre todo— su comportamiento ante fallos.

**Paso 9 — Correo.**
Eventos de dominio, listener asíncrono, servicio de correo y las 5 plantillas.

**Paso 10 — La lógica de negocio.**
`LoanService`, `BookService`, `ReservationService`, `AuthService`, `AdminService`.
Aquí es donde vive el valor de la aplicación.

**Paso 11 — Los controladores y el manejo global de errores.**
La capa más delgada: recibe, delega, responde.

**Paso 12 — Compilar y arreglar.**
Aquí apareció el primer problema real: un choque de nombres en `LookupResult`
(un método `unavailable()` estático y otro de instancia) que tumbaba el procesador de Lombok
y producía 200 errores engañosos de "cannot find symbol". Se renombró a `isUnavailable()`.

**Paso 13 — Las pruebas.**
107 pruebas en 4 niveles: unitarias, de cliente externo, de endpoints y de flujo completo.

**Paso 14 — El frontend.**
Tipos → cliente HTTP → estado → componentes → pantallas. En ese orden, porque cada capa
usa la anterior.

**Paso 15 — Docker.**
Dockerfiles multi-etapa, compose con los 4 servicios, nginx como proxy.

**Paso 16 — Probar contra el mundo real.**
Aquí aparecieron los dos bugs de Open Library (sección 7). Ninguna prueba con mocks los
habría encontrado, porque los mocks devolvían lo que yo *creía* que devolvía la API.

**Paso 17 — README, Postman, Git y revisión final.**

---

## 5. El backend capa por capa

### 5.1 `domain/` — las entidades

Son las clases que se convierten en tablas. Ejemplo, `Loan.java`:

```java
@Entity                          // "esto es una tabla"
@Table(name = "loans")           // se llama loans
public class Loan {

    @Id                                              // clave primaria
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // la genera la BD
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) // muchos préstamos, un libro
    @JoinColumn(name = "book_id", nullable = false)      // columna de la relación
    private Book book;

    @Version                     // control de concurrencia (ver más abajo)
    private Long version;
}
```

**`FetchType.LAZY`**: el libro no se trae de la BD hasta que alguien lo pide. Si fuera `EAGER`,
listar 100 préstamos dispararía 100 consultas extra.

**`@Version` (bloqueo optimista)**: Hibernate añade `WHERE version = ?` a cada `UPDATE`.
Si dos peticiones intentan modificar el mismo libro a la vez, la segunda actualiza 0 filas y
Spring lanza `OptimisticLockingFailureException` → el usuario recibe un 409 pidiéndole reintentar.
Sin esto, dos personas podrían prestarse el mismo ejemplar en el mismo milisegundo.

**Métodos de negocio dentro de la entidad**, no en un "utils" suelto:

```java
public boolean isReturnedLate() {
    return returnDate != null && returnDate.isAfter(dueDate);
}
```

La entidad sabe responder preguntas sobre sí misma. Eso es diseño orientado al dominio,
no una simple bolsa de datos.

### 5.2 `repository/` — acceso a datos

Se escribe una interfaz y Spring genera la implementación:

```java
public interface LoanRepository extends JpaRepository<Loan, Long> {

    // Spring lee el NOMBRE del método y escribe el SQL solo:
    boolean existsByBookIdAndReturnDateIsNull(Long bookId);

    // Cuando la consulta es compleja, se escribe explícita en JPQL:
    @Query("""
            select count(l) from Loan l
            where lower(l.borrowerEmail) = lower(:email)
              and l.returnDate is not null
              and l.returnDate > l.dueDate          -- se devolvió tarde
              and l.returnDate >= :since            -- dentro de la ventana
            """)
    long countLateReturnsSince(String email, LocalDate since);
}
```

Esta última consulta **es** la regla de los 3 atrasos. Fíjate que no hay ningún contador
guardado: los atrasos se calculan siempre desde los datos reales.

**`join fetch`** en varias consultas: trae el préstamo y su libro en **una sola** consulta.
Sin eso aparece el problema "N+1" (1 consulta para los préstamos + N para sus libros).

### 5.3 `service/` — la lógica de negocio

Aquí vive todo lo que hace que esto no sea un CRUD. `LoanService.create()` en orden:

```java
@Transactional                                    // todo o nada
public LoanResponse create(CreateLoanRequest request, AppUser currentUser) {

    // 1. ¿A nombre de quién es el préstamo?
    String borrowerEmail = ...;

    // 2. Un no-ADMIN solo puede prestarse a sí mismo
    if (currentUser.getRole() != Role.ADMIN && !borrowerEmail.equals(currentUser.getEmail()))
        throw new BusinessRuleException("FORBIDDEN_BORROWER", ...);

    // 3. ¿La cuenta está bloqueada por atrasos?
    assertNotBlocked(borrowerEmail);              // -> UserBlockedException (403)

    // 4. Traer el libro CON BLOQUEO: nadie más puede tocarlo hasta el commit
    Book book = bookRepository.findByIdForUpdate(request.bookId())...;

    // 5. ¿Está disponible? (o reservado para esta misma persona)
    Optional<Reservation> honored = assertBookCanBeLoaned(book, borrowerEmail);

    // 6. Crear el préstamo: hoy + 14 días
    Loan loan = Loan.builder()...dueDate(today.plusDays(rules.loanPeriodDays())).build();

    // 7. Cambiar el estado del libro
    book.setStatus(BookStatus.PRESTADO);

    // 8. Publicar el evento: el correo saldrá DESPUÉS del commit, en otro hilo
    events.publishEvent(new LoanCreatedEvent(saved.getId()));
}
```

**`@Transactional`**: si algo falla en el paso 7, se deshacen también los pasos 4-6.
La base nunca queda a medias.

**`findByIdForUpdate`** usa un bloqueo pesimista (`SELECT ... FOR UPDATE`). Es el cinturón
además del tirante del `@Version`: en la operación más crítica se bloquea la fila de entrada.

**El orden importa**: primero las validaciones baratas (permisos, bloqueo), después las
que tocan la base. No tiene sentido bloquear una fila para luego rechazar por permisos.

### 5.4 `web/` — controladores y DTOs

El controlador es deliberadamente tonto: recibe, delega y responde.

```java
@PostMapping
@Operation(summary = "Registra un préstamo...")          // esto alimenta Swagger
public ResponseEntity<LoanResponse> create(@Valid @RequestBody CreateLoanRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(loanService.create(request, CurrentUser.require()));
}
```

**`@Valid`** dispara Bean Validation sobre el DTO **antes** de entrar al método. Si el ISBN
viene vacío, la lógica de negocio nunca llega a ejecutarse.

**¿Por qué DTOs y no las entidades directamente?** Tres razones:
1. **Seguridad**: `AppUser` tiene `passwordHash`. Si devolviéramos la entidad, el hash saldría
   en el JSON. `UserResponse` simplemente no tiene ese campo.
2. **Estabilidad**: se puede renombrar una columna de la BD sin romper a quien consume la API.
3. **Cálculos para el cliente**: `LoanResponse` incluye `overdue` y `daysOverdue`, que no son
   columnas: se calculan al construir la respuesta para que el frontend no repita la lógica.

### 5.5 `exception/` + `GlobalExceptionHandler` — errores consistentes

Todas las excepciones de negocio heredan de una raíz que lleva su código y su status:

```java
public abstract class BusinessException extends RuntimeException {
    private final String code;        // "BOOK_NOT_AVAILABLE"
    private final HttpStatus status;  // 409
}
```

Y **un solo** manejador las traduce a JSON:

```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest req) {
    return ResponseEntity.status(ex.getStatus())
            .body(ApiError.of(ex.getStatus().value(), ex.getCode(), ex.getMessage(), req.getRequestURI()));
}
```

**La ventaja:** para añadir un error nuevo solo se crea la excepción. No hay que tocar ningún
controlador ni escribir un `try/catch`. El resultado siempre tiene la misma forma:

```json
{ "timestamp": "...", "status": 409, "code": "BOOK_NOT_AVAILABLE",
  "message": "El libro '1984' no esta disponible...", "path": "/api/loans" }
```

El handler cubre además: validación (con detalle campo por campo), credenciales incorrectas,
acceso denegado, violación de índice único, choque de concurrencia, y un `Exception` final
que **registra el stacktrace en el log pero nunca lo filtra al cliente**.

### 5.6 `config/` — cómo se ensambla todo

| Archivo | Qué configura |
|---|---|
| `SecurityConfig` | Qué rutas son públicas, cuáles piden ADMIN, CORS, BCrypt, el filtro JWT |
| `AsyncConfig` | El pool de hilos para los correos + activa `@Async` y `@Scheduled` |
| `CacheConfig` | Caffeine: 24 h de vigencia, máximo 1 000 ISBNs |
| `WebClientConfig` | El cliente HTTP hacia Open Library: timeouts, redirecciones, cabeceras |
| `OpenApiConfig` | Título de Swagger y el botón "Authorize" para pegar el token |
| `DataSeeder` | Crea las cuentas de prueba al arrancar, si no existen |
| `AppProperties` | Los parámetros de negocio (14 días, 3 atrasos, 90 días, 7 días de bloqueo) |
| `MailProperties` | Remitente y URL pública para los enlaces del correo |
| `OpenLibraryProperties` | URL, timeout y reintentos de la API externa |

**Por qué los parámetros de negocio son configurables:** para demostrar el bloqueo por atrasos
no hay que esperar 90 días ni recompilar; se cambia una variable de entorno.

### 5.7 `db/migration/` — el esquema como código

Flyway ejecuta los `.sql` en orden y anota cuáles ya corrió. El esquema deja de ser algo que
alguien creó a mano y pasa a ser código versionado y reproducible.

Dos detalles del `V1__initial_schema.sql` que vale la pena saber explicar:

```sql
-- Un libro no puede tener dos préstamos activos a la vez.
-- Es un índice PARCIAL: solo aplica a las filas sin devolver.
CREATE UNIQUE INDEX uk_loans_active_book ON loans (book_id) WHERE return_date IS NULL;

-- Nadie puede estar dos veces en la fila del mismo libro.
CREATE UNIQUE INDEX uk_reservations_active
    ON reservations (book_id, LOWER(requester_email))
    WHERE status IN ('PENDIENTE', 'NOTIFICADO');
```

Esto es una **red de seguridad en la base de datos**: aunque un bug se colara en el código Java,
PostgreSQL rechazaría el dato inconsistente. Las reglas críticas están defendidas en dos capas.

`spring.jpa.hibernate.ddl-auto: validate` cierra el círculo: al arrancar, Hibernate compara las
entidades con el esquema real y **se niega a arrancar** si no coinciden.

---

## 6. Los tres flujos de negocio, trazados

### 6.1 Prestar un libro

```
Usuario pulsa "Prestar" en el catálogo
   │
   ▼
CatalogComponent.prestar()                  frontend/src/app/features/catalog/pages/catalog/catalog.component.ts
   │  LoansService.create(book.id)
   ▼
authInterceptor                              añade "Authorization: Bearer <token>"
   │  POST /api/loans  {"bookId": 7}
   ▼
JwtAuthenticationFilter                      valida el token, pone al usuario en el contexto
   │
   ▼
SecurityConfig                               ¿la ruta pide algún rol? aquí: solo autenticado
   │
   ▼
LoanController.create()                      @Valid revisa el cuerpo
   │
   ▼
LoanService.create()          ◄── AQUÍ ESTÁN LAS REGLAS ──►
   │   1. ¿es a nombre propio o soy ADMIN?
   │   2. ¿la cuenta está bloqueada?         -> UserBlockedException (403)
   │   3. bloqueo la fila del libro
   │   4. ¿está DISPONIBLE?                  -> BookNotAvailableException (409)
   │   5. dueDate = hoy + 14
   │   6. book.status = PRESTADO
   │   7. publishEvent(LoanCreatedEvent)
   ▼
COMMIT de la transacción
   │
   ├──► respuesta HTTP 201 al navegador      (el usuario ya ve el resultado)
   │
   └──► NotificationEventListener            @Async: OTRO HILO, ya no bloquea
            │
            ▼
        NotificationService.sendLoanConfirmation()
            │  renderiza templates/email/loan-confirmation.html con Thymeleaf
            ▼
        JavaMailSender ──SMTP──► MailHog (localhost:8025)
```

**La clave está en la separación de la línea del COMMIT.** El usuario recibe su respuesta sin
esperar al servidor de correo. Y como el listener es `AFTER_COMMIT`, si la transacción hubiera
fallado, el correo **no** se habría enviado.

### 6.2 Devolver tarde y llegar al bloqueo

```
PUT /api/loans/42/return
   │
   ▼
LoanService.markReturned()
   │
   │  ¿es mi préstamo o soy ADMIN?           -> 403 si no
   │  ¿ya estaba devuelto?                   -> 409 si sí
   │  returnDate = hoy
   │
   ├─► ¿returnDate > dueDate?  SÍ → registerLateReturn()
   │        │
   │        │  countLateReturnsSince(email, hoy - 90 días)   ← consulta a la BD
   │        │
   │        └─► ¿el conteo llegó a 3?
   │                 SÍ → user.block(hoy + 7 días, "3 devoluciones con atraso...")
   │                      publishEvent(UserBlockedEvent)  → correo de bloqueo
   │
   └─► releaseBook(book)
            │
            ├─► ¿hay alguien en la lista de espera?
            │        NO  → book.status = DISPONIBLE          (fin)
            │        SÍ  → book.status = RESERVADO
            │              reserva.status = NOTIFICADO
            │              publishEvent(BookAvailableEvent)  → correo "ya está disponible"
```

**Detalle fino:** si la cuenta **ya** estaba bloqueada, no se le suma otra semana ni se le manda
otro correo. Se sanciona llegar al umbral, no cada devolución posterior.

### 6.3 La lista de espera de punta a punta

```
1. Ana pide "1984"           → libro PRESTADO
2. Luis entra a la fila      → Reservation(PENDIENTE), puesto 1
3. Ana devuelve              → el libro NO vuelve a DISPONIBLE:
                                 · libro    → RESERVADO
                                 · reserva  → NOTIFICADO
                                 · correo   → "Ya está disponible: 1984"
4. Otra persona intenta pedirlo   → 409 "está reservado para otra persona"
5. Luis lo pide                   → 201, y su reserva pasa a CUMPLIDO
```

Y si Luis **cancela** en el paso 4, `ReservationService.passTurnToNext()` le pasa el turno al
siguiente de la fila; si no queda nadie, el libro vuelve a DISPONIBLE. Sin eso, un libro podría
quedar atrapado en RESERVADO para siempre.

---

## 7. Integración con Open Library en detalle

### El flujo

```
Usuario escribe un ISBN y pulsa "Autocompletar desde ISBN"
   │
   ▼
GET /api/books/lookup/9780134685991
   │
   ▼
BookService.lookup()  →  OpenLibraryClient.lookupByIsbn()
                             │
                             ├─ ¿está en la caché Caffeine?  SÍ → devuelve en ~17 ms
                             │
                             └─ NO → llamada HTTP real (~2 s)
```

### La anotación clave

```java
@Cacheable(cacheNames = "openLibraryLookup", key = "#isbn", unless = "#result.isUnavailable()")
public LookupResult lookupByIsbn(String isbn) { ... }
```

Palabra por palabra:
- `cacheNames` — en qué caché se guarda.
- `key = "#isbn"` — la clave es el parámetro `isbn`.
- **`unless = "#result.isUnavailable()"`** — *no guardes el resultado si la API falló*.
  Esto es importante: si no estuviera, un timeout de un segundo dejaría ese ISBN marcado
  como "no funciona" durante 24 horas.

### Los tres resultados posibles y por qué se distinguen

```java
public enum Status { FOUND, NOT_FOUND, UNAVAILABLE }
```

| Situación | Respuesta HTTP | Qué le decimos al usuario |
|---|---|---|
| El ISBN existe allá | `200` + datos | El formulario se llena solo |
| El ISBN no existe allá | `404` | "No lo encontramos, escríbelo a mano" |
| La API no respondió | `503` | "Open Library no respondió, el libro se guarda igual" |

Devolver el mismo error en los tres casos habría sido más fácil, pero para el usuario significan
cosas distintas: en un caso el dato no existe, en el otro el servicio está caído.

### Comportamiento ante fallos: dos caminos distintos a propósito

| Endpoint | Si la API falla |
|---|---|
| `GET /api/books/lookup/{isbn}` | **Devuelve 503.** El usuario está esperando ese dato: hay que decirle |
| `POST /api/books` | **No falla.** Guarda el libro con lo que escribió la persona, `enrichedFromExternal = false` |

Esto es exactamente lo que pedía el enunciado: *"si el servicio externo no responde a tiempo o
falla, el registro del libro no debe romperse"*.

### Los dos bugs reales que se encontraron probando contra la API de verdad

Merecen su propia sección porque son el mejor argumento de que se probó en serio.

**Bug 1 — La API responde 302, no 200.**

```
GET https://openlibrary.org/isbn/9780134685991.json
→ 302 Found
→ Location: /books/OL31838212M.json
```

WebClient **no sigue redirecciones por defecto**. El síntoma era un error confuso:
`Content type 'text/html' not supported`. La integración fallaba siempre.

```java
HttpClient.create()
    .followRedirect(true)     // ← la corrección
```

**Bug 2 — El autor no está donde uno esperaría.**

Open Library separa la *edición* (este libro impreso concreto) de la *obra* (`work`, el libro
como idea). Muchas ediciones **no traen el campo `authors`**: el autor vive en la obra, y encima
con otra forma:

```jsonc
// en la edición:  {"authors": [{"key": "/authors/OL..."}]}
// en la obra:     {"authors": [{"author": {"key": "/authors/OL..."}}]}   ← anidado distinto
```

La corrección: el cliente pide la obra **una sola vez** y de ahí saca autores **y** temas.

**Por qué ninguna prueba con mocks los habría encontrado:** los mocks devolvían lo que yo
*creía* que devolvía la API. Solo la llamada real reveló la diferencia. Después de descubrirlos,
se añadieron pruebas con `MockWebServer` que reproducen ambos casos, para que no vuelvan a pasar.

**Consecuencia:** el timeout quedó en **5 s y no en los 3 s** del enunciado, porque una consulta
son mínimo dos viajes de red por la redirección. Está documentado y es configurable.

---

## 8. El sistema de correo en detalle

### Los 5 correos

| Correo | Cuándo se dispara | Quién lo dispara |
|---|---|---|
| Confirmación de préstamo | Al registrar el préstamo | Evento `LoanCreatedEvent` |
| Recordatorio de vencimiento | 1-2 días antes | Tarea diaria 8:00 |
| Cuenta bloqueada | Al tercer atraso | Evento `UserBlockedEvent` |
| Libro disponible | Al devolver con lista de espera | Evento `BookAvailableEvent` |
| Préstamo vencido | Ya pasada la fecha | Tarea diaria 8:30 |

### Por qué asíncrono: el problema y la solución

**El problema.** Si el correo se enviara dentro del método que crea el préstamo, el usuario
esperaría a que el servidor SMTP conteste. Si el SMTP está lento, la petición tarda 10 segundos.
Si está caído, **el préstamo falla** aunque no tenga nada de malo.

**La solución, en dos anotaciones:**

```java
@Async                                                       // (2)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)   // (1)
public void onLoanCreated(LoanCreatedEvent event) {
    notificationService.sendLoanConfirmation(event.loanId());
}
```

1. **`AFTER_COMMIT`** — no ejecutes esto hasta que la transacción se haya confirmado.
   Evita el caso horrible de mandar "tu préstamo quedó registrado" y que después se deshaga.
2. **`@Async`** — ejecútalo en otro hilo (el pool `mailExecutor`, 2-8 hilos).
   La petición HTTP responde sin esperar.

Y una tercera protección: `NotificationService.send()` **captura sus propios errores**.
Un fallo de SMTP se registra en el log y ahí muere. Nunca tumba una operación de negocio válida.

### Las plantillas Thymeleaf

En vez de concatenar strings, se renderiza HTML:

```java
Context context = new Context(new Locale("es", "CO"));
context.setVariables(model);                          // los datos
String html = templateEngine.process("email/loan-confirmation", context);
```

Los 5 correos comparten cabecera, ficha del libro y pie mediante `_fragments.html`:

```html
<th:block th:replace="~{email/_fragments :: header('Tu préstamo quedó registrado', '#1d4ed8')}"></th:block>
```

**Los estilos van en línea (`style="..."`) a propósito**: los clientes de correo (Gmail, Outlook)
ignoran las hojas de estilo externas y con frecuencia también las etiquetas `<style>`.

### Las tareas programadas y la idempotencia

```java
@Scheduled(cron = "${app.scheduling.reminder-cron:0 0 8 * * *}")   // seg min hora día mes díaSemana
@Transactional
public void sendDueSoonReminders() {
    List<Loan> loans = loanRepository.findDueSoonWithoutReminder(hoy, hoy.plusDays(2));
    for (Loan loan : loans) {
        loan.setReminderSentAt(Instant.now());        // ← la marca
        notificationService.sendDueSoonReminder(loan.getId());
    }
}
```

**Por qué existen `reminderSentAt` y `overdueNoticeSentAt`** (los pedía el enunciado):
la consulta solo trae préstamos con esa marca en `null`. Al marcarlos dentro de la misma
transacción, aunque la tarea se ejecute dos veces —por un reinicio, o porque alguien la lanza a
mano— **el mismo aviso no sale dos veces**. A eso se le llama una operación idempotente.

**Para verlo funcionando sin esperar a las 8 a.m.**: en `.env` pon `REMINDER_CRON=0 * * * * *`
(cada minuto), reinicia el backend y mira MailHog.

---

## 9. Seguridad en detalle

### Qué es un JWT y por qué se usa aquí

Un JWT es una cadena de tres partes separadas por puntos: `cabecera.datos.firma`.
Los datos van codificados (no cifrados: **cualquiera puede leerlos**), pero la firma solo la
puede generar quien tiene el secreto.

```java
Jwts.builder()
    .subject(user.getEmail())              // de quién es
    .claim("role", user.getRole().name())  // qué rol tiene
    .expiration(Date.from(expiry))         // hasta cuándo vale
    .signWith(key)                         // firma con el secreto
    .compact();
```

**La ventaja:** el servidor no guarda sesiones. Cada petición trae su token, se verifica la firma
y listo. Se puede escalar a 10 servidores sin compartir estado entre ellos.

**La consecuencia:** nunca se pone información sensible dentro del token, porque es legible.

### El recorrido de cada petición

```
Petición con "Authorization: Bearer eyJ..."
   │
   ▼
JwtAuthenticationFilter        ¿hay token? ¿la firma es válida? ¿no está vencido?
   │                           SÍ → carga el usuario y lo pone en el SecurityContext
   │                           NO → sigue como anónimo (no lanza error todavía)
   ▼
SecurityConfig                 ¿esta ruta necesita autenticación? ¿algún rol?
   │                           falla → RestAuthenticationEntryPoint (401)
   │                                   o RestAccessDeniedHandler (403)
   ▼
@PreAuthorize del controlador  segunda capa de defensa
   │
   ▼
El método se ejecuta
```

**Por qué la autorización está en dos sitios** (`SecurityConfig` **y** `@PreAuthorize`):
si alguien mueve o renombra una ruta, la regla del `SecurityConfig` deja de aplicar, pero la
anotación sigue pegada al método. Es defensa en profundidad, y cuesta una línea.

### BCrypt

```java
passwordHash(passwordEncoder.encode(request.password()))   // al registrar
passwordEncoder.matches(request.password(), user.getPasswordHash())   // al entrar
```

BCrypt es **de un solo sentido**: del hash no se puede volver a la contraseña. Además es
deliberadamente lento e incorpora una sal aleatoria, así que dos usuarios con la misma
contraseña tienen hashes distintos.

Verificado en una prueba automática (`AuthControllerIT`): lo guardado empieza por `$2` (prefijo
BCrypt) y **no** es igual a la contraseña escrita.

### Detalles pequeños que suman

- **No se revela si un correo existe.** Contraseña incorrecta y usuario inexistente devuelven
  exactamente el mismo `401 BAD_CREDENTIALS`. Si se distinguieran, se podría averiguar quién
  tiene cuenta.
- **CSRF desactivado, y es correcto.** CSRF protege sesiones basadas en cookie, que el navegador
  envía solas. Aquí el token va en una cabecera que el atacante no puede forzar.
- **El bloqueo por atrasos no impide iniciar sesión.** `isAccountNonLocked()` devuelve `true` a
  propósito: una cuenta bloqueada tiene que poder entrar y **devolver** sus libros; si no pudiera,
  nunca saldría del bloqueo. La restricción vive en `LoanService`, que es donde corresponde.
- **Ningún secreto en el repositorio.** `.env` está en `.gitignore`; las contraseñas de prueba no
  están en ninguna migración SQL, las crea `DataSeeder` desde una variable de entorno, y la app
  **escribe una advertencia en el log** si arranca con la contraseña por defecto.

---

## 10. El frontend en detalle

### El cliente HTTP: una sola puerta de salida

Todo el tráfico pasa por `HttpClient` con dos interceptores funcionales registrados en
`app.config.ts`. Ahí, y solo ahí, ocurren cuatro cosas:

```typescript
// 1. authInterceptor: se añade el token a cada llamada (salvo login y registro)
return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));

// 2. errorInterceptor: los errores del backend llegan al componente ya normalizados
return throwError(() => ApiError.from(error));   // code, message, fieldErrors

// 3. Un 401 fuera del login cierra la sesión y manda al login
if (error.status === 401 && !req.url.includes('/api/auth/login')) { auth.logout(); router.navigate(['/login']); }

// 4. Un fallo de red no revienta la app
if (error.status === 0) return new ApiError(0, 'NETWORK_ERROR', 'No se pudo contactar el servidor...');
```

**Lo importante de `ApiError`:** conserva el **código de negocio** del backend. Así el frontend
puede reaccionar a `USER_BLOCKED` de forma distinta que a `DUPLICATE_ISBN`, sin leer el texto
del mensaje (que puede cambiar).

### El estado global con signals

`AuthStore` guarda lo que varias pantallas necesitan: quién eres y tu token.

```typescript
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly _token = signal<string | null>(null);
  private readonly _user = signal<User | null>(null);
  readonly isAuthenticated = computed(() => this._token() !== null);
  readonly isAdmin = computed(() => this._user()?.role === 'ADMIN');
  constructor() { this.restore(); }         // ← sobrevive a un F5 (localStorage)
  async login(email, password) { ... this.persist(); }
}
```

**Por qué signals y no NgRx:** para una app de 4 pantallas, NgRx exige acciones, reducers,
selectors y effects. Un servicio con signals da lo mismo en 30 líneas y los componentes
`OnPush` se actualizan solos. La prueba aceptaba explícitamente "Signals/Services en Angular".

### `Cargable`: los tres estados de siempre

Cada pantalla que carga datos necesita responder tres preguntas: ¿está cargando? ¿falló?
¿hay datos? Escribir eso a mano en cada pantalla se repite; `Cargable` lo resuelve una vez:

```typescript
readonly libros = new Cargable<Page<Book>>('No se pudo cargar el catálogo.');
effect(() => this.libros.cargar(() => this.books.list({ search: this.buscado(), ... })));
```

Y además **cancela la petición anterior** (anula la suscripción) cuando cambias de filtro o sales
de la pantalla. Sin eso, escribir rápido en el buscador dispara 8 peticiones y la que llega
tarde puede pisar el resultado correcto. El buscador, además, lleva `debounceTime(350)`.

En la plantilla se traduce a esto, que es exactamente lo que pedía el enunciado
("nada de tragarse un 500 en silencio"):

```html
@if (libros.loading() && !libros.data()) { <app-shelf-skeleton /> }
@if (!libros.loading() && libros.error(); as error) { <app-failed [message]="error" (retry)="libros.recargar()" /> }
@if (libros.data()?.content?.length === 0) { <app-empty message="No hay libros…" /> }
```

### Las pantallas

| Pantalla | Lo que hay que saber explicar |
|---|---|
| **Login** | Valida en el cliente **las mismas reglas** que el backend (mínimo 8 caracteres, formato de correo). No sustituye la validación del servidor: la duplica para dar respuesta inmediata |
| **Catálogo** | Búsqueda con *debounce* de 350 ms, filtro por estado, paginación. El botón cambia entre "Prestar" y "Lista de espera" según el estado del libro. Un ADMIN ve además "Registrar" y "Eliminar" |
| **Alta de libro** | El botón "Autocompletar desde ISBN" llama al *preview*, llena el formulario y **lo deja editable**. Si la API falla, muestra un aviso naranja y deja seguir a mano |
| **Mis préstamos** | Tres bloques: activos (con aviso de vencido y días de atraso), lista de espera (con la posición en la fila) e historial. Tras devolver, **refresca el usuario** porque la devolución tardía pudo haber bloqueado la cuenta |
| **Panel admin** | 8 estadísticas, cuentas bloqueadas con botón de levantar el bloqueo, y todos los préstamos activos ordenados por fecha de vencimiento |

**Nota sobre dónde vive la administración de libros:** registrar y eliminar libros está en el
Catálogo (visible solo para ADMIN) en lugar de duplicarlo en el Panel. Es la pantalla donde ya
están los libros; tenerlos en dos sitios sería mantener dos veces lo mismo.

### El proxy: dos modos, sin CORS en ninguno

- **Desarrollo**: `ng serve` proxea `/api` → `localhost:8080` (`proxy.conf.json`).
- **Docker**: nginx proxea `/api` → `backend:8080` (`nginx.conf`).

En ambos casos el navegador cree que todo viene del mismo origen, así que **no hay peticiones
cruzadas**. La configuración CORS del backend existe igualmente, por si alguien despliega el
frontend en otro dominio.

---

## 11. Las pruebas, explicadas

**107 pruebas, 0 fallos**, en cuatro niveles. Cada nivel prueba algo que los otros no pueden.

### Nivel 1 — Unitarias: la lógica sola (38 pruebas)

> Los números de cada nivel son **pruebas ejecutadas**, que es lo que reporta Maven.
> No coinciden con el número de métodos `@Test`, porque un `@ParameterizedTest` es un
> solo método que se ejecuta una vez por cada caso de datos.

Sin base de datos, sin red, sin Spring. Milisegundos por prueba.

```java
@Test
@DisplayName("al tercer atraso en 90 dias bloquea la cuenta una semana y avisa por correo")
void thirdLateReturnBlocksAccountForOneWeek() {
    when(loanRepository.countLateReturnsSince(...)).thenReturn(3L);   // simulo 3 atrasos

    loanService.markReturned(100L, borrower);

    assertThat(borrower.isBlocked()).isTrue();
    assertThat(borrower.getBlockedUntil()).isBetween(dentroDe6Dias, dentroDe8Dias);
}
```

**Mockito** sustituye los repositorios por dobles que devuelven lo que yo decida. Así puedo
probar "¿qué pasa con exactamente 3 atrasos?" sin crear 3 préstamos reales.

### Nivel 2 — El cliente externo con MockWebServer (10 pruebas)

`MockWebServer` levanta un servidor HTTP falso en un puerto local. Le digo qué responder y
compruebo cómo reacciona el cliente:

| Prueba | Qué simula | Qué verifica |
|---|---|---|
| `mapsFullResponse` | Respuesta completa | Título, autor, año, portada y temas bien mapeados |
| `followsTheRedirectOpenLibraryReturns` | Un **302** | Que sigue la redirección (el bug 1 de la sección 7) |
| `resolvesAuthorsFromWorkWhenEditionHasNone` | Edición sin autores | Que los busca en la obra (el bug 2) |
| `unknownIsbnReturnsNotFound` | Un **404** | Devuelve NOT_FOUND, no un error |
| `serverErrorReturnsUnavailable` | Un **500** | Devuelve UNAVAILABLE y **no revienta** |
| `timeoutReturnsUnavailable` | Respuesta a 5 s | Corta por timeout en vez de colgar la petición |
| `malformedJsonReturnsUnavailable` | JSON roto | Se degrada, no propaga la excepción |

Esto es lo que el enunciado pedía como *"incluyendo el caso en que la API falla"*.

### Nivel 3 — Endpoints con MockMvc (19 pruebas)

Simula peticiones HTTP completas —con seguridad incluida— sin abrir un puerto:

```java
mockMvc.perform(get("/api/books"))                       // sin token
       .andExpect(status().isUnauthorized())
       .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
```

Verifica lo que solo se ve desde fuera: que sin token da 401, que un BIBLIOTECARIO recibe 403 en
un endpoint de ADMIN, que un ISBN duplicado da 409 con su código, y que **el formato de error es
siempre el mismo**.

### Nivel 4 — Flujo completo contra base de datos real (14 pruebas)

Levantan la aplicación entera sobre H2 y recorren el flujo de negocio de punta a punta:
prestar → reservar → devolver → bloqueo → schedulers.

Y el correo se verifica **de verdad**, con GreenMail (un servidor SMTP embebido):

```java
notificationService.sendLoanConfirmation(loan.getId());

assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
MimeMessage message = greenMail.getReceivedMessages()[0];
assertThat(message.getSubject()).isEqualTo("Prestamo confirmado: Clean Code");
assertThat(message.getAllRecipients()[0].toString()).isEqualTo("ana@biblioteca.co");
assertThat(GreenMailUtil.getBody(message)).contains("Fecha limite de devolucion");
```

**La diferencia es importante:** no se comprueba "que se llamó al método de enviar". Se comprueba
que **un correo llegó a un buzón**, con el asunto, el destinatario y el contenido correctos.

### Por qué las pruebas usan H2 y no PostgreSQL

Las migraciones usan SQL específico de PostgreSQL (índices parciales con `WHERE`, `TIMESTAMPTZ`).
Escribirlas en SQL "portable" habría empobrecido el esquema real solo para complacer a H2.

La solución: en el perfil de test, Flyway se desactiva y el esquema lo genera Hibernate desde las
mismas entidades. ¿Y quién garantiza que las entidades y las migraciones coincidan?
`ddl-auto: validate`: al arrancar contra PostgreSQL de verdad, **la aplicación no arranca** si
hay la más mínima diferencia.

### Verificación manual contra el sistema real

Además de las 88 automáticas, se recorrió el flujo completo contra el stack levantado
(Docker + PostgreSQL + MailHog real), incluyendo el bloqueo por 3 atrasos, el desbloqueo por
ADMIN y las tareas programadas disparando. **Ahí es donde aparecieron los dos bugs de Open
Library** — que las pruebas con mocks, por definición, no podían encontrar.

---

## 12. Docker, explicado

### Qué hace `docker compose up --build`

Levanta cuatro contenedores conectados en una red privada:

| Contenedor | Imagen | Puerto | Para qué |
|---|---|---|---|
| `anaquel-db` | postgres:16-alpine | 5432 | Base de datos |
| `anaquel-mailhog` | mailhog/mailhog | 1025 (SMTP) · 8025 (web) | Recibe los correos y los muestra |
| `anaquel-api` | se construye del código | 8080 | El backend |
| `anaquel-web` | se construye del código | 5173 | nginx con el frontend compilado |

**El orden importa** y está declarado:

```yaml
depends_on:
  db:
    condition: service_healthy      # espera a que Postgres RESPONDA, no solo a que arranque
```

Sin `condition: service_healthy`, el backend intentaría conectarse antes de que Postgres esté
listo y moriría en el arranque.

### El Dockerfile del backend: build en dos etapas

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build    # etapa 1: compilar
COPY pom.xml .
RUN mvn dependency:go-offline                 # ← las dependencias en su propia capa
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine            # etapa 2: solo ejecutar
COPY --from=build /build/target/*.jar app.jar
RUN addgroup -S app && adduser -S app -G app
USER app                                      # ← no corre como root
```

Tres decisiones que vale la pena explicar:

1. **Dos etapas**: la imagen final lleva solo el JRE y el `.jar`. Ni Maven, ni el compilador, ni
   el código fuente. Imagen más pequeña y menos superficie de ataque.
2. **`pom.xml` se copia antes que `src`**: Docker cachea capas. Si solo cambia el código, no
   vuelve a descargar todas las dependencias.
3. **`USER app`**: si alguien logra ejecutar algo dentro del contenedor, no es root.

### Las variables de entorno

Nada de configuración quemada. `docker-compose.yml` lee de `.env`:

```yaml
JWT_SECRET: ${JWT_SECRET:-cambia-este-secreto-de-desarrollo-local-min-32-bytes}
```

La sintaxis `${VAR:-valor}` significa "usa `VAR`, y si no existe, este valor por defecto".
Así el proyecto arranca recién clonado, pero en producción todo se sobrescribe sin tocar código.

---

## 13. Glosario de anotaciones y conceptos

### Anotaciones de Spring que aparecen en el proyecto

| Anotación | Qué hace |
|---|---|
| `@SpringBootApplication` | Marca la clase de arranque; activa la autoconfiguración |
| `@RestController` | Esta clase atiende HTTP y devuelve JSON |
| `@Service` | Contiene lógica de negocio; Spring la gestiona como componente |
| *(sin anotación)* | Las interfaces que extienden `JpaRepository` **no llevan `@Repository`**: Spring Data las detecta y las implementa sola |
| `@Configuration` | Clase que define beans de configuración |
| `@Bean` | El objeto que devuelve este método lo gestiona Spring |
| `@Transactional` | Todo el método es una transacción: o se guarda todo, o nada |
| `@Valid` | Valida el cuerpo de la petición antes de ejecutar el método |
| `@PreAuthorize("hasRole('ADMIN')")` | Solo un ADMIN puede ejecutar este método |
| `@Cacheable` | Guarda el resultado; la próxima vez con los mismos parámetros no ejecuta |
| `@Async` | Ejecuta en otro hilo; el llamador no espera |
| `@Scheduled(cron = ...)` | Ejecuta según un horario |
| `@TransactionalEventListener(AFTER_COMMIT)` | Reacciona a un evento, pero solo si la transacción se confirmó |
| `@RestControllerAdvice` | Manejo de errores centralizado para todos los controladores |
| `@Entity` / `@Table` | Esta clase es una tabla |
| `@ManyToOne` | Relación "muchos a uno" (muchos préstamos apuntan a un libro) |
| `@ElementCollection` | Lista de valores simples en su propia tabla (los temas del libro) |
| `@Version` | Bloqueo optimista contra modificaciones simultáneas |
| `@ConfigurationProperties` | Enlaza un bloque del `application.yml` a una clase |

### Conceptos

| Concepto | Explicado en una frase |
|---|---|
| **Inyección de dependencias** | Una clase declara qué necesita y Spring se lo entrega; no lo crea ella misma |
| **DTO** | Objeto de transporte: lo que entra y sale de la API, separado de la entidad de base de datos |
| **Bloqueo optimista** | "Asumo que nadie más lo tocó; si me equivoco, fallo y reintento" (`@Version`) |
| **Bloqueo pesimista** | "Reservo la fila para mí hasta que termine" (`SELECT ... FOR UPDATE`) |
| **Idempotencia** | Ejecutarlo dos veces produce el mismo resultado que ejecutarlo una |
| **Problema N+1** | Traer 100 préstamos y disparar 100 consultas extra para sus libros. Se evita con `join fetch` |
| **Migración** | Un `.sql` versionado que lleva el esquema de un estado al siguiente |
| **Índice parcial** | Un índice que solo aplica a las filas que cumplen una condición (`WHERE return_date IS NULL`) |
| **JWT** | Token firmado que lleva quién eres; el servidor no guarda sesión |
| **BCrypt** | Hash de un solo sentido, lento a propósito y con sal, para contraseñas |
| **Degradación controlada** | Si un servicio externo falla, la aplicación sigue funcionando con menos información |
| **CORS** | Regla del navegador sobre qué orígenes pueden llamar a una API |
| **Debounce** | Esperar a que el usuario deje de escribir antes de lanzar la búsqueda |

---

## 14. Preguntas que te pueden hacer y cómo responderlas

**"¿Por qué no guardaste un contador de atrasos en la tabla de usuarios?"**
Porque un contador se desincroniza. Si alguien corrige la fecha de devolución de un préstamo a
mano, el contador queda mintiendo para siempre. La consulta
`countLateReturnsSince()` calcula el número desde los datos reales y siempre dice la verdad.
El costo es una consulta indexada, que es despreciable.

**"¿Cómo evitas que dos personas presten el mismo libro a la vez?"**
En tres capas. Primero, `SELECT ... FOR UPDATE` bloquea la fila del libro durante la transacción.
Segundo, `@Version` detecta modificaciones concurrentes en cualquier otro camino. Tercero, un
índice único parcial en PostgreSQL (`WHERE return_date IS NULL`) hace imposible que existan dos
préstamos activos del mismo libro, aunque hubiera un bug en el código Java.

**"¿Por qué el correo es asíncrono?"**
Para que la petición HTTP no dependa del servidor SMTP. Si el correo fuera síncrono y el SMTP
estuviera caído, un préstamo perfectamente válido fallaría. Con
`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, el usuario recibe su 201 de inmediato y
el correo sale después, en otro hilo. Y como es `AFTER_COMMIT`, nunca se manda un correo de una
transacción que terminó en rollback.

**"¿Qué pasa si Open Library se cae?"**
Depende del endpoint, a propósito. En el *preview* (`/lookup/{isbn}`) devuelvo 503, porque el
usuario está esperando ese dato y hay que decírselo. En el registro (`POST /api/books`) no falla:
el libro se guarda con lo que la persona escribió y se marca `enrichedFromExternal = false`.
Es literalmente lo que pedía el enunciado.

**"¿Por qué distingues 404 de 503 en el lookup?"**
Porque significan cosas distintas para el usuario. Un 404 dice "ese ISBN no existe en Open
Library, escríbelo a mano". Un 503 dice "el servicio está caído, inténtalo luego o escríbelo a
mano". Devolver lo mismo en ambos casos habría sido más fácil de programar y peor de usar.

**"¿Por qué el bloqueo no impide iniciar sesión?"**
Porque una cuenta bloqueada tiene que poder devolver sus libros. Si no pudiera entrar, nunca
saldría del bloqueo: es una trampa lógica. El enunciado dice "bloqueada **para pedir nuevos
préstamos**", así que la restricción vive en `LoanService`, no en la autenticación.

**"¿Por qué desactivaste CSRF?"**
Porque CSRF protege contra ataques que abusan de cookies de sesión, que el navegador envía
automáticamente. Aquí la autenticación es un token en una cabecera `Authorization`, que un sitio
atacante no puede hacer que el navegador envíe. Activar CSRF en una API stateless con JWT sería
ceremonia sin beneficio.

**"¿Por qué las pruebas usan H2 y no PostgreSQL con Testcontainers?"**
Por reproducibilidad: las pruebas corren sin Docker y sin red, en segundos. El riesgo de H2 es
que el esquema real difiera del de las pruebas, y eso lo cubre `ddl-auto: validate`: contra
PostgreSQL, la aplicación se niega a arrancar si las entidades y las migraciones no coinciden.
Si el proyecto creciera, Testcontainers sería el siguiente paso.

**"¿Qué harías distinto si esto fuera a producción?"**
Cinco cosas, por orden de prioridad:
1. **Reintento de correos**: hoy un fallo de SMTP se registra y se pierde. Haría falta una cola
   con reintentos (Redis o una tabla `outbox`).
2. **Bloqueo distribuido para el scheduler**: con dos instancias de la app, ambas ejecutarían la
   tarea diaria. ShedLock lo resuelve.
3. **Caché compartida**: Caffeine es por instancia. Con varias instancias, Redis.
4. **Refresh tokens**: hoy el token dura 8 horas y luego hay que volver a entrar.
5. **Observabilidad**: métricas de Micrometer y trazas, para saber cuánto tarda Open Library.

**"¿Qué fue lo más difícil?"**
La integración con Open Library, y no por el código sino por la API: responde con un 302 que
WebClient no sigue por defecto, y el autor no está en la edición sino en la obra, con una forma
anidada distinta. Ninguna prueba con mocks lo habría detectado, porque los mocks devolvían lo que
yo asumía. Solo apareció al probar contra la API real. Después añadí pruebas con `MockWebServer`
que reproducen ambos casos para que no vuelva a pasar.
