# Comandos para correr Anaquel

Requisitos: **Docker Desktop** (o Docker + Compose v2). Para desarrollo sin Docker: Java 21, Maven 3.9, Node 20.

## 1. Levantar todo (un solo comando)

```bash
cp .env.example .env            # solo la primera vez; no hay secretos reales
docker compose up --build -d    # ~3 min la primera vez
docker compose ps               # backend y db deben decir "healthy"
```

| Qué | Dónde |
|---|---|
| Aplicación | http://localhost:5173 |
| API + Swagger | http://localhost:8080/swagger-ui.html |
| Correos enviados (MailHog) | http://localhost:8025 |
| PostgreSQL | localhost:5432 · usuario/clave `anaquel` / `anaquel` |

Usuarios de prueba (los crea la app al arrancar):

| Correo | Contraseña | Rol |
|---|---|---|
| `admin@anaquel.app` | `Admin123*` | ADMIN |
| `lectura@anaquel.app` | `Admin123*` | BIBLIOTECARIO |

## 2. Pruebas automáticas

```bash
cd backend && mvn test          # 107 pruebas (H2 + GreenMail + MockWebServer; no necesita Docker)
cd frontend && npm install && npm test   # 43 pruebas Jasmine/Karma (Chrome headless)
cd frontend && npm run lint:no-inline && npm run build
```

> En macOS `mvn test` imprime un `ERROR` de Netty (`MacOSDnsServerAddressStreamProvider`): es cosmético, todas las pruebas pasan.

## 3. Postman

Importa `postman/Biblioteca.postman_collection.json` y `postman/Biblioteca.postman_environment.json`.
Ejecuta primero **1. Auth → Login (ADMIN)** (guarda el token) y luego la carpeta **6. Escenario completo**.

## 4. Ver el recordatorio y el aviso de vencido sin esperar a las 8:00

```bash
# Pide un libro en la app y luego adelanta las fechas:
docker compose exec db psql -U anaquel -d anaquel -c \
  "UPDATE loans SET loan_date = CURRENT_DATE - 13, due_date = CURRENT_DATE + 1 WHERE return_date IS NULL;"
# En .env: REMINDER_CRON=0 * * * * *  y  OVERDUE_CRON=30 * * * * *  (cada minuto), y reinicia la API:
docker compose up -d backend
```

Los correos aparecen en MailHog en menos de un minuto. Guion completo paso a paso: [`PLAN-DE-PRUEBAS.md`](PLAN-DE-PRUEBAS.md).

## 5. Desarrollo sin Docker

```bash
docker compose up -d db mailhog          # solo infraestructura
cd backend && mvn spring-boot:run        # http://localhost:8080
cd frontend && npm install && npm start  # http://localhost:5173 (proxea /api al 8080)
```

## 6. Logs, reinicio y apagado

```bash
docker compose logs -f backend   # ver la API
docker compose restart backend   # tras cambiar .env
docker compose down              # apaga y conserva los datos
docker compose down -v           # apaga y borra los datos (vuelve a los 5 libros de ejemplo)
```
