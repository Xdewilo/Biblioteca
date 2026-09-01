# Plan de pruebas — Anaquel

Guion ordenado para probar **todo** a mano, de principio a fin, en unos 20 minutos.
Cada paso dice qué hacer y **qué debes ver**. Si algo no coincide, es un fallo.

> Antes de empezar, en una terminal:
>
> ```bash
> cd anaquel
> cp .env.example .env          # solo la primera vez
> docker compose up --build -d  # ~3 min la primera vez
> docker compose ps             # los 4 contenedores "Up" (api y db "healthy")
> ```
>
> Pestañas que vas a usar: la app (http://localhost:5173), **MailHog** (http://localhost:8025)
> y Swagger (http://localhost:8080/swagger-ui.html).

Cuentas: `admin@anaquel.app` / `Admin123*` (ADMIN) · `lectura@anaquel.app` / `Admin123*` (BIBLIOTECARIO).

---

## Recorrido según lo que evalúan (PDF, punto 8)

El PDF reparte 100 puntos en 8 áreas. Este es el orden en que conviene demostrarlas, con los
pasos del guion que cubren cada una. Sigue las secciones 0 → 8 y tendrás todo cubierto.

| Área (puntos) | Lo que quieren ver | Pasos del guion |
|---|---|---|
| Docker, docs y calidad (4) | `docker compose up` levanta todo con un comando; README con cómo correrlo, variables, usuario ADMIN y MailHog | Preparación · README §2, §9 |
| Testing automatizado (8) | Reglas de negocio, MockMvc, GreenMail y MockWebServer (con fallo de la API) | 0 |
| API REST y seguridad (14) | JWT, roles, errores JSON consistentes, springdoc | 1.2–1.8 · 6.3 · 7.1–7.5 |
| Modelo de datos y persistencia (12) | Loan/Book/AppUser/Reservation, Flyway V1–V3, ISBN y correo únicos | 1.6 · 3.6 · `docker compose exec db psql -U anaquel -d anaquel -c '\dt'` |
| Reglas de negocio (18) | Disponible → prestado, ISBN duplicado, cuenta bloqueada, 3 atrasos en 90 días, lista de espera → RESERVADO | 2.6 · 3.6 · 4.1–4.8 · 5.1–5.7 |
| Correo (18) | 4 correos reales en MailHog, asíncronos (evento AFTER_COMMIT) y programados (cron) con Thymeleaf | 2.7 · 4.5 · 5.4 · 5.8–5.9 |
| Open Library (14) | Autocompletar por ISBN, caché Caffeine, y que un fallo externo **no rompa** el alta | 3.2–3.9 · 7.2 |
| Frontend (12) | Login, catálogo con búsqueda + Autocompletar, mis préstamos con vencidos, panel admin; estados de carga y error | 2.1–2.8 · 3.1 · 5.2 · 6.1–6.2 · 8.1–8.3 |

**Antes de entregar** (regla dura del PDF: nada de secretos en el repo):

- `git status` limpio y `git ls-files | grep -i env` solo muestra `.env.example` (el `.env` real está en `.gitignore`).
- Los valores de `.env.example` y `docker-compose.yml` son placeholders de desarrollo; el README lo dice.
- El PDF de la prueba es confidencial: **no lo subas** al repositorio público (hoy no está versionado, pero
  sigue en el historial de git en `93622cb`; si vas a publicar el repo, crea el remoto desde un historial limpio).

---

## 0. Pruebas automáticas (2 min)

```bash
cd backend && mvn test        # 107 pruebas, BUILD SUCCESS (el ERROR de Netty en macOS es cosmético)
cd frontend && npm test       # 43 pruebas Jasmine, "TOTAL: 43 SUCCESS"
```

---

## 1. Login y registro (3 min)

| # | Haz | Debes ver |
|---|---|---|
| 1.1 | Abre http://localhost:5173 | Te manda a `/login`. Panel oscuro con la cita a la izquierda; el formulario entra en cascada |
| 1.2 | Pulsa **Entrar** con todo vacío | Bajo cada campo: "El correo no tiene un formato válido." y "La contraseña debe tener al menos 8 caracteres." **No** hay petición al servidor |
| 1.3 | `lectura@anaquel.app` con clave `mala1234` | Aviso rojo "Correo o contraseña incorrectos." (no dice si el correo existe) |
| 1.4 | Pulsa **Registrarme** | Aparece el campo Nombre; el formulario se recompone con un fundido |
| 1.5 | Regístrate como `Ana Pérez` / `ana@anaquel.app` / `Secreta123` | Entras directo al catálogo. Aviso verde "Cuenta creada". En la barra lateral: **Ana Pérez · Bibliotecario** (nunca ADMIN, aunque lo intentes por API) |
| 1.6 | Cierra sesión y vuelve a registrarte con `ana@anaquel.app` | Aviso rojo "Ya existe una cuenta registrada con el correo ana@anaquel.app." (409 del backend) |
| 1.7 | Sin sesión, escribe a mano `http://localhost:5173/admin` | Te manda a `/login?from=/admin`. Al entrar como admin, aterrizas en Administración |
| 1.8 | Como `lectura`, escribe `http://localhost:5173/admin` | Vuelves al catálogo sin pantalla de error, y en la barra lateral **no** hay "Administración" |
| 1.9 | Pulsa F5 estando dentro | Sigues dentro (la sesión sobrevive a la recarga) |

---

## 2. Catálogo (3 min) — entra como `lectura`

| # | Haz | Debes ver |
|---|---|---|
| 2.1 | Mira el catálogo | 5 libros con **portada real** (Clean Architecture, El nombre de la rosa, Rayuela, Refactoring) y uno con la inicial "L" (La casa de los espíritus: Open Library no tiene su portada). Chips de estado con punto de color. Las tarjetas entran en cascada |
| 2.2 | Escribe `rosa` en el buscador | Solo "El nombre de la rosa". La búsqueda espera 350 ms (no dispara por tecla). La × limpia |
| 2.3 | Escribe `9788437604572` | Encuentra Rayuela por ISBN |
| 2.4 | Escribe `zzz` | "No hay libros que coincidan con la búsqueda." con sugerencia |
| 2.5 | Filtro **Disponibles / Prestados / Reservados** | El indicador blanco se desliza; la lista se filtra |
| 2.6 | Pulsa **Pedir** en Rayuela | Aviso verde "«Rayuela» es tuyo. Devuélvelo antes del dd/mm/aaaa". La tarjeta pasa a **PRESTADO** y muestra "✓ En tus manos · Devolver" |
| 2.7 | Abre **MailHog** | Correo "Prestamo confirmado: Rayuela" a `lectura@anaquel.app`, con portada, fecha y fecha límite (+14 días) |
| 2.8 | Ancho de ventana < 900 px (o móvil) | Barra superior con iconos, 2 libros por fila, botones a todo el ancho |

---

## 3. Alta de libro (3 min) — entra como `admin`

| # | Haz | Debes ver |
|---|---|---|
| 3.1 | **Añadir libro** | Diálogo con fundido; Escape lo cierra |
| 3.2 | Escribe `1234-1234-1234-1234` en ISBN | Al instante: "Llevas 16 dígitos: un ISBN tiene 10 o 13." Botón Autocompletar gris |
| 3.3 | Escribe `9780132350885` (último dígito cambiado) | "Los dígitos no cuadran con el de control…" |
| 3.4 | Pulsa el ejemplo **9788478884452** | Sin tocar nada más: título *Harry Potter y la piedra filosofal*, autor J. K. Rowling, año, portada, temas. Los campos rellenados parpadean en verde. Aviso "Datos traídos de openlibrary.org" |
| 3.5 | Corrige el título a mano y **Guardar en el catálogo** | Aviso verde; el libro aparece en el catálogo con su portada |
| 3.6 | Vuelve a añadir el **mismo ISBN** | Aviso naranja "Ojo: este ISBN ya está en el catálogo"; al guardar, error rojo `DUPLICATE_ISBN` |
| 3.7 | Escribe `9790000000001` (válido, pero Open Library no lo conoce) | Aviso naranja "Open Library no tiene datos para el ISBN 9790000000001. Puedes registrar el libro a mano." Escribe título y autor y guarda → se crea igual |
| 3.8 | **Quitar** en un libro disponible | Diálogo de confirmación propio (no `window.confirm`); "Sí, quitarlo" lo elimina. En un libro prestado no hay botón Quitar |
| 3.9 | (Opcional) apaga la red y prueba 3.4 | "Open Library no respondió. Escribe los datos a mano: el libro se guarda igual." |

---

## 4. Lista de espera con dos usuarios (4 min)

Usa dos navegadores (o una ventana de incógnito): **admin** y **lectura**.

| # | Haz | Debes ver |
|---|---|---|
| 4.1 | `lectura` ya tiene Rayuela (2.6). Como **admin**, en Rayuela pulsa **Esperar turno** | "Estás en la fila por «Rayuela», puesto 1". La tarjeta muestra "En la fila · puesto 1 · Salir" |
| 4.2 | Como **lectura**, en Rayuela | "✓ En tus manos · Devolver" (no te deja reservar tu propio préstamo) |
| 4.3 | `lectura` → **Mis préstamos** → **Devolver** Rayuela | Aviso "devuelto a tiempo". Pasa a "Ya devueltos · A tiempo" |
| 4.4 | En el catálogo, Rayuela | Estado **RESERVADO** (no vuelve a DISPONIBLE porque alguien esperaba) |
| 4.5 | **MailHog** | Correo "Ya esta disponible: Rayuela" al admin, con el plazo de 48 h |
| 4.6 | Como **admin**, catálogo | Rayuela muestra **Recogerlo**. Púlsalo → "«Rayuela» ya es tuyo" y pasa a "En tus manos" |
| 4.7 | Como **lectura**, intenta **Esperar turno** en Rayuela y luego **Salir** | Entra en la fila; Salir → "Saliste de la lista de espera" |
| 4.8 | `admin` → Mis préstamos → Devolver | Sin nadie en la fila, Rayuela vuelve a **DISPONIBLE** |

---

## 5. Atrasos, bloqueo y correos programados (4 min)

El plazo es de 14 días y el bloqueo llega al **tercer atraso en 90 días**; para no esperar, se
adelantan las fechas en la base de datos (es la única "trampa" del guion y está pensada para la demo).

> Los atrasos se acumulan: si repites esta sección, la cuenta se bloqueará antes del tercer
> libro. Para empezar de cero: `docker compose down -v && docker compose up -d`.

```bash
# 5.1 Como lectura, pide 3 libros distintos en el catálogo. Luego:
docker compose exec db psql -U anaquel -d anaquel -c \
  "UPDATE loans SET loan_date = CURRENT_DATE - 17, due_date = CURRENT_DATE - 3 WHERE return_date IS NULL AND borrower_email = 'lectura@anaquel.app';"
```

| # | Haz | Debes ver |
|---|---|---|
| 5.2 | `lectura` → Mis préstamos (recarga) | Los tres con chip rojo **"Vencido · 3 días"** |
| 5.3 | Devuelve los tres, uno a uno | 1.º y 2.º: "devuelto con atraso". Al **3.º**: aviso rojo arriba "Tu cuenta está bloqueada hasta el …" y chip "Cuenta bloqueada" en la barra lateral |
| 5.4 | **MailHog** | Correo "Tu cuenta quedo bloqueada temporalmente…" con los 3 atrasos y la fecha |
| 5.5 | Catálogo como `lectura` | Botones **Pedir** y **Esperar turno** deshabilitados; aviso ámbar arriba |
| 5.6 | Como `admin` → Administración | Estadística **Bloqueadas: 1**; en "Cuentas bloqueadas", lectura con motivo y fecha; **Levantar bloqueo** → aviso verde y la fila desaparece |
| 5.7 | `lectura` recarga (F5) | Desaparecen el aviso y el chip; puede volver a pedir (la app vuelve a pedir `/api/auth/me` al arrancar) |

**Recordatorio y aviso de vencido** (tareas programadas a las 8:00 y 8:30):

```bash
# Pide un libro como lectura y deja que venza mañana:
docker compose exec db psql -U anaquel -d anaquel -c \
  "UPDATE loans SET loan_date = CURRENT_DATE - 13, due_date = CURRENT_DATE + 1 WHERE return_date IS NULL;"
# Para no esperar a las 8:00, pon en .env los crons cada minuto y reinicia la API:
#   REMINDER_CRON=0 * * * * *     OVERDUE_CRON=30 * * * * *
docker compose up -d backend
```

| # | Debes ver |
|---|---|
| 5.8 | En MailHog, en menos de un minuto: "Tu prestamo vence pronto: …" (una sola vez, aunque el cron se repita: `reminder_sent_at` lo evita) |
| 5.9 | Si además pones `loan_date = CURRENT_DATE - 15, due_date = CURRENT_DATE - 1`: "Prestamo vencido: …" y en Administración el chip rojo **Vencido** |

Vuelve a dejar los crons por defecto en `.env` al terminar.

---

## 6. Administración (2 min) — `admin`

| # | Haz | Debes ver |
|---|---|---|
| 6.1 | Entra en Administración | 8 tarjetas cuyos números **cuentan** hasta su valor; los tonos (verde/ámbar/rojo) marcan disponibles, prestados, vencidos y bloqueadas |
| 6.2 | "Libros fuera" | Todos los préstamos activos ordenados por fecha límite, con miniatura, quién lo tiene y chip de plazo ("Vence en N días", ámbar si ≤ 2, rojo si vencido) |
| 6.3 | Como `lectura`, `GET /api/admin/stats` en Swagger | **403** con el mismo JSON de error (`code`, `message`, `path`) |

---

## 7. API, Swagger y Postman (2 min)

| # | Haz | Debes ver |
|---|---|---|
| 7.1 | Swagger → **Auth → login** → *Try it out* con el admin → copia `token` → **Authorize** | Cualquier endpoint responde. `GET /api/books` devuelve la página con `content` |
| 7.2 | `GET /api/books/lookup/9780132350884` | 200 con título, autor, año, portada y temas. Con `9780000000002` → **404**. Con `123` → **400 INVALID_ISBN** |
| 7.3 | `POST /api/auth/register` con `"role": "ADMIN"` | 201 pero `user.role` = **BIBLIOTECARIO** |
| 7.4 | `GET /api/no-existe` | **404 NOT_FOUND** (no 500) |
| 7.5 | Postman: importa `postman/*.json`, ejecuta **1. Auth → Login (ADMIN)** y luego la carpeta **6. Escenario completo** | Todos los tests en verde |

---

## 8. Errores que nunca se tragan (1 min)

| # | Haz | Debes ver |
|---|---|---|
| 8.1 | `docker compose stop backend` y recarga el catálogo | En ~3 s, pantalla de error "El servidor no responde ahora mismo. Inténtalo en un momento." con botón **Reintentar** (no una pantalla en blanco) |
| 8.2 | `docker compose start backend` y **Reintentar** | Vuelve el catálogo |
| 8.3 | Borra el token: DevTools → Application → Local Storage → `anaquel-auth` → recarga | Te manda al login; cualquier 401 cierra la sesión |

---

## Apagar

```bash
docker compose down        # conserva la base de datos
docker compose down -v     # borra también los datos (vuelve a los 5 libros de ejemplo)
```
