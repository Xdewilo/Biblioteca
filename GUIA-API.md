# Guía de la API de Anaquel (Swagger) — cómo explicarla sin trabarse

> **Para qué sirve este documento.** Es el guion para sentarse frente a Swagger UI y explicar
> el sistema completo en 10 minutos. Trae qué hacer clic, qué decir y qué contestar si preguntan.
>
> - Si quieres **levantar el proyecto** → [README.md](README.md)
> - Si quieres **entender cómo está hecho por dentro** → [DOCUMENTACION.md](DOCUMENTACION.md)
> - Si quieres **explicar la API en voz alta** → estás en el archivo correcto

---

## Índice

1. [Qué es Swagger y por qué está aquí](#1-qué-es-swagger-y-por-qué-está-aquí)
2. [Abrirlo y autenticarse (2 minutos)](#2-abrirlo-y-autenticarse-2-minutos)
3. [El guion de la demo en 10 minutos](#3-el-guion-de-la-demo-en-10-minutos)
4. [Referencia de los 20 endpoints](#4-referencia-de-los-20-endpoints)
5. [Catálogo de errores](#5-catálogo-de-errores)
6. [Los modelos de datos](#6-los-modelos-de-datos)
7. [Cómo se genera esta documentación](#7-cómo-se-genera-esta-documentación)
8. [Preguntas que te pueden hacer sobre la API](#8-preguntas-que-te-pueden-hacer-sobre-la-api)

---

## 1. Qué es Swagger y por qué está aquí

**En una frase:** Swagger UI es una página web donde están listados todos los endpoints de la API,
con sus parámetros, sus respuestas y un botón para **ejecutarlos de verdad** desde el navegador.

**La parte importante para explicar:** esa página **no se escribió a mano**. La genera la librería
`springdoc-openapi` leyendo el código Java al arrancar. Es decir:

> Si mañana alguien añade un endpoint o cambia un campo, la documentación se actualiza sola.
> Es imposible que quede desfasada, que es exactamente lo que le pasa a un Word con la lista de
> endpoints.

**Cómo funciona por dentro:** springdoc recorre los controladores y lee sus anotaciones
(`@Operation`, `@ApiResponses`, `@Schema`) para producir un archivo estándar llamado **OpenAPI**.
Swagger UI es solo un visor bonito de ese archivo.

```
Código Java  ──springdoc──►  /v3/api-docs (JSON)  ──►  Swagger UI (la página)
                                    │
                                    └──► docs/openapi.json (copia versionada en el repo)
```

Ese estándar sirve para más cosas: se puede importar en Postman, o generar automáticamente un
cliente en TypeScript, Python o Java.

---

## 2. Abrirlo y autenticarse (2 minutos)

```bash
docker compose up --build       # si aún no está corriendo
```

Abre **http://localhost:8080/swagger-ui.html**

Lo primero que se ve es una portada que explica el sistema, los dos roles y las tres reglas de
negocio. Esa portada también sale del código (`OpenApiConfig.java`).

### Autenticarse — los 4 pasos

| # | Qué haces | Qué dices mientras lo haces |
|---|---|---|
| 1 | Despliega **Autenticacion → POST /api/auth/login** y pulsa **Try it out** | "Casi toda la API está protegida, así que lo primero es identificarse" |
| 2 | Pega el cuerpo de abajo y pulsa **Execute** | "Mando correo y contraseña" |
| 3 | Copia el valor de `token` de la respuesta | "El servidor me devuelve un **JWT**: un carné firmado que dice quién soy y qué rol tengo" |
| 4 | Pulsa **Authorize** (arriba a la derecha), pega el token y **Authorize → Close** | "Ahora Swagger lo enviará en cada petición. No hay que escribir 'Bearer', él lo pone" |

```json
{ "email": "admin@anaquel.app", "password": "Admin123*" }
```

**Si preguntan por qué un token y no una sesión:**
> Porque el servidor no guarda nada. El token viaja en cada petición y lleva la firma dentro, así
> que cualquier instancia del backend puede validarlo sin consultar una base de sesiones. Eso
> permite escalar a varios servidores sin que compartan estado.

### Las dos cuentas de prueba

| Correo | Contraseña | Rol | Sirve para enseñar |
|---|---|---|---|
| `admin@anaquel.app` | `Admin123*` | ADMIN | Todo el sistema |
| `lectura@anaquel.app` | `Admin123*` | BIBLIOTECARIO | **Que los permisos funcionan**: recibe 403 en los endpoints de ADMIN |

---

## 3. El guion de la demo en 10 minutos

Este recorrido cuenta la historia completa del sistema. Sigue el orden.

### Acto 1 — El catálogo (1 min)

**`GET /api/books`** → Execute

> "Aquí está el catálogo. Viene paginado, y con `search` puedo buscar por título, autor o ISBN
> en un solo campo."

Repite con `search = clean`. Apunta al campo **`status`** de un libro:

> "Cada libro tiene un estado: DISPONIBLE, PRESTADO o RESERVADO. Ese estado es el que gobierna
> todas las reglas que vienen ahora."

**Anota el `id` de un libro que esté DISPONIBLE.** Lo vas a usar todo el rato.

### Acto 2 — El autocompletado desde ISBN (2 min) ⭐

**`GET /api/books/lookup/{isbn}`** con `isbn = 9780134685991`

> "Este es uno de los puntos fuertes. Le doy **solo el ISBN** y la aplicación va a Open Library,
> una API pública, y me trae título, autor, año, portada y temas. Esto alimenta el botón
> *Autocompletar desde ISBN* del formulario."

**Ejecuta la misma petición otra vez** y señala el tiempo de respuesta:

> "Fíjate: la primera tardó cerca de dos segundos, la segunda unos 17 milisegundos. Va a una
> **caché en memoria** (Caffeine), porque los datos de un ISBN no cambian nunca. No tiene sentido
> molestar a la API externa dos veces por el mismo libro."

**Lo que más impresiona — mira los códigos de respuesta documentados:**

> "Y mira que este endpoint documenta un **404** y un **503**, que no son lo mismo a propósito:
> el 404 es 'ese ISBN no existe en Open Library, escríbelo a mano'; el 503 es 'la API está caída,
> inténtalo luego'. Para el usuario son problemas distintos y merecen mensajes distintos."

**El remate:**

> "Y lo más importante: al **registrar** un libro, si Open Library falla, **no pasa nada**. El
> libro se guarda con lo que la persona escribió. Un servicio externo caído no puede impedir que
> la biblioteca trabaje."

### Acto 3 — Prestar (2 min) ⭐

**`POST /api/loans`** con `{ "bookId": <el id que anotaste> }`

> "Al prestar pasan cuatro cosas a la vez, dentro de una transacción: se valida que el libro esté
> disponible, que mi cuenta no esté bloqueada, el libro pasa a PRESTADO y se calcula la fecha
> límite a 14 días."

Señala en la respuesta `loanDate`, `dueDate`, y luego `overdue` / `daysOverdue`:

> "Estos dos últimos no son columnas de la base de datos: se calculan al construir la respuesta,
> para que el frontend no tenga que repetir la lógica de negocio."

**Ahora abre http://localhost:8025 (MailHog):**

> "Y ahí está el correo de confirmación. Lo importante es **cuándo** salió: la API me respondió
> de inmediato, sin esperar al servidor de correo. Se publica un evento y el correo sale después
> del *commit*, en otro hilo. Si el servidor de correo estuviera caído, el préstamo se registra
> igual."

**Repite exactamente el mismo `POST /api/loans`:**

> "Y aquí está la regla: 409 con el código `BOOK_NOT_AVAILABLE`. El mismo libro no se puede
> prestar dos veces."

### Acto 4 — La lista de espera (2 min) ⭐ *el mejor momento de la demo*

**`POST /api/reservations`** con `{ "bookId": <el mismo> }`

> "Como el libro está prestado, me pongo en la lista de espera. Me devuelve mi puesto en la fila."

**`PUT /api/loans/{id}/return`** con el id del préstamo:

> "Ahora lo devuelvo."

**`GET /api/books/{id}`** — y aquí está el punto:

> "Mira el estado: **RESERVADO**, no DISPONIBLE. Esta es la regla que más me gusta del sistema.
> Cuando devuelves un libro que alguien estaba esperando, no vuelve al montón: queda apartado para
> el primero de la fila, y le llega un correo avisándole."

Vuelve a MailHog y enseña el correo *"Ya está disponible"*.

> "Y si esa persona cancela su reserva, el turno pasa automáticamente al siguiente de la fila.
> Si no queda nadie, ahí sí vuelve a DISPONIBLE."

**Y el remate — `POST /api/reservations/{id}/confirm`:**

> "Fíjate que quien tiene el turno no necesita volver al catálogo a buscar el libro: su reserva
> trae `readyToConfirm: true` y `holdExpiresAt`, y con este endpoint confirma el préstamo en un
> solo paso. El plazo también importa: si no confirma en 48 horas, una tarea horaria le pasa el
> turno al siguiente. Sin eso, una persona que no apareciera dejaría el libro fuera de circulación
> para siempre."

### Acto 5 — Los permisos (1 min)

**`GET /api/admin/stats`** (sigues como ADMIN) → funciona.

Ahora pulsa **Authorize → Logout**, entra con `lectura@anaquel.app / Admin123*`, autoriza de
nuevo y **repite `GET /api/admin/stats`**:

> "403. Misma petición, distinto rol. Y fíjate en el cuerpo del error: tiene exactamente la misma
> forma que cualquier otro error de la API."

### Acto 6 — Los errores (1 min)

Despliega cualquier endpoint y muestra la lista de códigos:

> "Todos los endpoints documentan sus errores, y todos devuelven el mismo formato: `timestamp`,
> `status`, `code`, `message` y `path`. Lo hace un único `@RestControllerAdvice`: no hay ni un
> `try/catch` repartido por los controladores."

```json
{
  "timestamp": "2026-08-18T00:56:54.974Z",
  "status": 409,
  "code": "BOOK_NOT_AVAILABLE",
  "message": "El libro '1984' no esta disponible para prestamo (estado actual: PRESTADO).",
  "path": "/api/loans"
}
```

> "El campo clave es `code`. Es estable: el frontend reacciona a `USER_BLOCKED` o a
> `DUPLICATE_ISBN`, nunca al texto del mensaje, que puede cambiar o traducirse."

### Acto 7 — El cierre (1 min)

> "Y para probar que las reglas funcionan de verdad, hay **107 pruebas automáticas**. El correo se
> verifica con un servidor SMTP embebido —se comprueba que el mensaje llega con su asunto y su
> destinatario, no solo que se llamó a un método— y la API externa se simula con MockWebServer,
> incluidos los casos en que se cae."

---

## 4. Referencia de los 20 endpoints

Agrupados igual que en Swagger. **🔒 = requiere token · 👑 = solo ADMIN**

### Autenticación

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| `POST` | `/api/auth/register` | Crea una cuenta y devuelve el token | `201` · `400` datos inválidos · `409` correo ya usado |
| `POST` | `/api/auth/login` | Autentica y devuelve el token JWT | `200` · `400` faltan datos · `401` credenciales |
| `GET` | `/api/auth/me` 🔒 | Datos del usuario del token, incluido si está bloqueado | `200` · `401` |

> **Detalle que puedes mencionar:** el 401 es idéntico si el correo no existe o si la contraseña
> está mal. Si fueran distintos, se podría averiguar quién tiene cuenta en el sistema.

### Catálogo

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| `GET` | `/api/books` 🔒 | Lista con búsqueda por título/autor/ISBN y filtro por estado | `200` · `401` |
| `GET` | `/api/books/{id}` 🔒 | Detalle de un libro | `200` · `404` |
| `GET` | `/api/books/lookup/{isbn}` 🔒 | **Previsualiza desde Open Library sin guardar nada** | `200` · `404` no existe allá · `503` API caída |
| `POST` | `/api/books` 👑 | Registra un libro; completa los vacíos desde Open Library | `201` · `400` · `403` · `409` ISBN duplicado |
| `DELETE` | `/api/books/{id}` 👑 | Elimina, **solo si está DISPONIBLE** | `204` · `403` · `404` · `409` |

**Parámetros de `GET /api/books`:**

| Parámetro | Qué hace | Ejemplo |
|---|---|---|
| `search` | Busca en título, autor **e** ISBN a la vez, sin distinguir mayúsculas | `clean` |
| `status` | Filtra por `DISPONIBLE`, `PRESTADO` o `RESERVADO` | `DISPONIBLE` |
| `page` | Página, empezando en 0 | `0` |
| `size` | Tamaño de página, máximo 100 | `12` |

### Préstamos

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| `POST` | `/api/loans` 🔒 | **Registra un préstamo**: valida disponibilidad y bloqueo | `201` · `400` · `403` bloqueado · `404` · `409` no disponible |
| `GET` | `/api/loans/mine` 🔒 | Tus préstamos, con el cálculo de vencimiento | `200` · `401` |
| `GET` | `/api/loans` 👑 | Todos los préstamos del sistema | `200` · `403` |
| `PUT` | `/api/loans/{id}/return` 🔒 | **Devuelve**: aplica atrasos y lista de espera | `200` · `403` · `404` · `409` ya devuelto |

> **La frase para `PUT /return`:** "Este endpoint hace tres cosas: marca la devolución, y si fue
> tarde suma un atraso —al tercero en 90 días bloquea la cuenta—, y si alguien esperaba el libro,
> lo aparta para esa persona y le manda un correo."

### Lista de espera

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| `POST` | `/api/reservations` 🔒 | Entra a la fila de un libro prestado. Devuelve tu puesto | `201` · `400` · `403` · `404` · `409` |
| `POST` | `/api/reservations/{id}/confirm` 🔒 | **Confirma en un paso el préstamo del libro que te guardaban** | `201` · `403` · `404` · `409` no te toca aún |
| `GET` | `/api/reservations/mine` 🔒 | Tus reservas con su estado y posición | `200` · `401` |
| `DELETE` | `/api/reservations/{id}` 🔒 | Cancela; **si tenías el turno, pasa al siguiente** | `204` · `403` · `404` · `409` |

### Administración

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| `GET` | `/api/admin/stats` 👑 | Contadores: prestados, vencidos, por vencer, bloqueadas | `200` · `403` |
| `GET` | `/api/admin/users` 👑 | Todas las cuentas | `200` · `403` |
| `GET` | `/api/admin/users/blocked` 👑 | Cuentas con bloqueo vigente | `200` · `403` |
| `POST` | `/api/admin/users/{id}/unblock` 👑 | **Levanta el bloqueo antes del plazo** | `200` · `403` · `404` |

---

## 5. Catálogo de errores

Todos comparten la misma forma. Lo que cambia es el `code`, que es el campo estable.

| `code` | HTTP | Cuándo aparece | Qué decir |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Falta un campo o tiene mal formato | "Trae además `errors[]` con el detalle campo por campo" |
| `MISSING_BOOK_DATA` | 400 | Open Library falló **y** no escribiste título ni autor | "Se pide el dato, no se devuelve un 500" |
| `BAD_CREDENTIALS` | 401 | Correo o contraseña incorrectos | "Idéntico si el correo no existe, para no filtrar quién tiene cuenta" |
| `UNAUTHORIZED` | 401 | Falta el token o está vencido | "Lo genera Spring Security, y aun así sale con nuestro formato" |
| `FORBIDDEN` | 403 | Tu rol no alcanza | "Es la regla de roles funcionando" |
| `USER_BLOCKED` | 403 | Cuenta bloqueada por 3 atrasos | "La regla de negocio principal" |
| `FORBIDDEN_BORROWER` | 403 | Un no-ADMIN intenta prestar a nombre de otro | "Si no, cualquiera llenaría de atrasos una cuenta ajena" |
| `FORBIDDEN_LOAN` | 403 | Intentas devolver un préstamo que no es tuyo | |
| `NOT_FOUND` | 404 | El id no existe (o el ISBN no está en Open Library) | |
| `BOOK_NOT_AVAILABLE` | 409 | El libro está PRESTADO, o RESERVADO para otro | "La regla del punto 2 del enunciado" |
| `DUPLICATE_ISBN` | 409 | Ya hay un libro con ese ISBN | "El ISBN es único, garantizado también en la base de datos" |
| `LOAN_ALREADY_RETURNED` | 409 | Ese préstamo ya se devolvió | |
| `BOOK_NOT_DELETABLE` | 409 | El libro no está DISPONIBLE o tiene préstamo activo | |
| `BOOK_ALREADY_AVAILABLE` | 409 | Intentas reservar un libro que está libre | "Le decimos que lo pida prestado directamente" |
| `DUPLICATE_RESERVATION` | 409 | Ya estabas en esa fila | |
| `RESERVATION_NOT_CANCELABLE` | 409 | Ya estaba cancelada o cumplida | |
| `RESERVATION_NOT_READY` | 409 | Intentas confirmar una reserva a la que aún no le toca | "El turno respeta el orden de llegada" |
| `CONCURRENT_MODIFICATION` | 409 | Dos personas tocaron el mismo registro a la vez | "Es el bloqueo optimista protegiendo los datos" |
| `EXTERNAL_LOOKUP_FAILED` | 503 | Open Library no respondió | "El único caso en que un servicio externo se refleja al usuario" |
| `INTERNAL_ERROR` | 500 | Cualquier fallo no previsto | "El stacktrace va al log, **nunca** al cliente" |

---

## 6. Los modelos de datos

En Swagger están abajo del todo, en **Schemas**. Los cuatro que importan:

### `BookResponse` — un libro

| Campo | Qué es |
|---|---|
| `status` | `DISPONIBLE` · `PRESTADO` · `RESERVADO` — **gobierna todas las reglas** |
| `coverUrl` | Portada de Open Library. `null` si no tiene |
| `subjects` | Temas traídos de la API externa |
| `enrichedFromExternal` | `true` si los datos vinieron de Open Library, `false` si se escribieron a mano |

### `LoanResponse` — un préstamo

| Campo | Qué es |
|---|---|
| `loanDate` / `dueDate` | La fecha límite siempre es `loanDate + 14 días` |
| `returnDate` | `null` mientras no se devuelva |
| `overdue`, `daysOverdue`, `returnedLate` | **Calculados**, no son columnas |

> **La frase:** "Estos tres se calculan al construir la respuesta. Así el frontend no tiene que
> saber la regla de negocio, solo pintar lo que le llega."

### `ReservationResponse` — un puesto en la fila

| Campo | Qué es |
|---|---|
| `status` | `PENDIENTE` → `NOTIFICADO` (le tocó el turno) → `CUMPLIDO` o `CANCELADO` |
| `queuePosition` | 1 = eres el siguiente |

### `ApiError` — el error

Ya explicado en la sección 5. **Un solo formato para toda la API.**

> **Lo que NO aparece en ningún esquema, y conviene señalarlo:** el `passwordHash`. `UserResponse`
> simplemente no tiene ese campo, así que es imposible que se filtre por accidente. Por eso se
> usan DTOs y no se devuelven las entidades directamente.

---

## 7. Cómo se genera esta documentación

```
backend/src/main/java/.../web/controller/*.java     ← las anotaciones
                    │
                    │  springdoc-openapi lee el código al arrancar
                    ▼
        http://localhost:8080/v3/api-docs            ← el JSON estándar
                    │
       ┌────────────┴────────────┐
       ▼                         ▼
Swagger UI (la página)     docs/openapi.json (copia en el repo)
```

**Las anotaciones que producen cada parte de la página:**

| En el código | Qué produce en Swagger |
|---|---|
| `@Tag(name = "Prestamos")` | El grupo plegable |
| `@Operation(summary = "...")` | El título de cada endpoint |
| `@ApiResponses({...})` | La tabla de códigos de respuesta |
| `@Schema(description, example)` | La descripción y el ejemplo de cada campo |
| `@Parameter(description)` | La ayuda de cada parámetro de consulta |
| `OpenApiConfig` | La portada y el botón **Authorize** |

**El archivo `docs/openapi.json` se regenera solo.** Lo produce una prueba automática
(`OpenApiSpecIT`) que además verifica que:

1. Estén las 11 rutas que exige la prueba técnica.
2. Esté declarado el esquema de seguridad JWT.
3. **Cada** endpoint tenga resumen y al menos un error documentado.
4. Todos los errores apunten al mismo esquema `ApiError`.

```bash
cd backend && mvn test -Dtest=OpenApiSpecIT     # regenera docs/openapi.json
```

> **Por qué esto importa:** si alguien añade un endpoint y se olvida de documentar sus errores,
> **la prueba falla**. La documentación no puede quedarse atrás del código.
>
> Y no hace falta Docker ni PostgreSQL: la prueba usa H2 en memoria.

**Ese `openapi.json` también sirve para:** importarlo en Postman, generar un cliente TypeScript o
Java automáticamente, o publicarlo en un portal de APIs.

---

## 8. Preguntas que te pueden hacer sobre la API

**"¿Por qué el token va en una cabecera y no en una cookie?"**
Porque la API no tiene estado. Una cookie la envía el navegador sola, lo que obliga a protegerse
de CSRF; una cabecera `Authorization` la pone explícitamente el cliente, así que un sitio atacante
no puede provocarla. Además así la misma API sirve a una app móvil sin cambiar nada.

**"¿Por qué `POST /api/books` acepta un libro con solo el ISBN?"**
Porque es el caso de uso real: quien registra libros tiene el código de barras delante, no la
ficha bibliográfica. La aplicación completa el resto desde Open Library. Y si la API externa
falla, el formulario sigue funcionando a mano.

**"¿Qué pasa si dos personas piden el mismo libro en el mismo instante?"**
Una gana y la otra recibe un 409. Está protegido en tres capas: un bloqueo pesimista sobre la fila
del libro durante la transacción, un bloqueo optimista con `@Version`, y un índice único parcial
en PostgreSQL que hace imposible que existan dos préstamos activos del mismo libro.

**"¿Por qué `/api/loans/mine` y no `/api/users/{id}/loans`?"**
Porque el id del usuario ya viaja dentro del token. Si la ruta llevara el id, habría que validar
en cada petición que ese id es el tuyo, y sería un sitio fácil por donde filtrar los préstamos de
otra persona. `mine` no admite ese error.

**"¿Por qué devolver es `PUT` y no `POST`?"**
Porque es idempotente en intención: devolver el préstamo 42 lleva a un estado concreto y
conocido. `POST` se usa para crear recursos nuevos (préstamos, reservas, libros). De hecho, un
segundo `PUT` sobre el mismo préstamo responde 409 explicando que ya se devolvió.

**"¿Por qué el `code` además del `status` HTTP?"**
Porque un mismo 409 puede significar cinco cosas distintas: ISBN duplicado, libro no disponible,
préstamo ya devuelto, reserva duplicada… El HTTP dice *qué categoría* de problema es; el `code`
dice *cuál exactamente*, y es lo que el frontend puede leer sin depender del texto del mensaje.

**"¿Está paginado el catálogo?"**
Sí, `GET /api/books` devuelve `content`, `totalElements`, `totalPages` y `number`. Los listados
de préstamos y reservas no lo están porque son de una sola persona y no crecen sin límite; si
creciera el volumen, se paginan igual.

**"¿Cómo probarías esta API sin Swagger?"**
Con la colección de Postman que viene en `postman/`: 38 peticiones organizadas en 6 carpetas, con
tests automáticos. La carpeta *Escenario completo* reproduce el flujo de negocio de punta a punta.
