# ClassChat

ClassChat is a persistent real-time messaging application built with **Java 21**, **Spring Boot**, **Spring Security**, **PostgreSQL**, and **WebSocket + STOMP**. The browser UI is plain HTML, CSS, and JavaScript and is served by the same application.

## Features

- Sign up with an email, a unique `@username`, and a password; sign in with either email or username.
- Verify new email addresses during sign-up with a six-digit code that expires after 10 minutes. After verification, the new account is signed in automatically; ordinary sign-in does not ask for a code.
- Existing accounts that still need verification can request a code from the sign-in screen and then sign in normally.
- Passwords are stored as PBKDF2 hashes. Authenticated browser sessions use a secure same-origin cookie and CSRF protection.
- Search for other users by username and start a private conversation.
- Create group conversations and add or remove members. Only the group creator can manage membership.
- Messages are saved in PostgreSQL and delivered live to conversation members. Conversation history remains available after leaving and signing back in; the UI loads it in pages of 50.
- Conversation lists show unread counts. Opening a conversation while the page is visible marks its messages as read.
- Optional Web Push notifications can be enabled in the browser and can arrive while ClassChat is closed.
- Conversation membership is checked on message sends and history requests. Live messages are sent only to each current member's private queue, so removed members do not receive new messages.

Password reset is available for verified accounts using an expiring email code. Email verification and password reset require an SMTP server. Web Push requires VAPID keys and HTTPS outside localhost.

## How a message is delivered

1. A signed-in browser sends `{ "content": "Hello" }` to `/app/conversations/{id}/send`.
2. The server identifies the sender from the authenticated HTTP session, verifies membership, adds the server timestamp, and stores the message.
3. Spring delivers the saved message to each current member's private queue. Only authenticated conversation members can subscribe to their queue.
4. The browser displays the message and retrieves saved history from `/api/conversations/{id}/messages`.

The browser cannot choose a sender identity or send to a conversation it does not belong to.

## Project structure

```text
src/main/java/com/classchat/
├── config/       Security, WebSocket, and clock configuration
├── controller/   Authentication, conversation REST APIs, and STOMP messages
├── dto/          Validated API and messaging payloads
├── model/        Accounts, conversations, members, and saved messages
├── repository/   Spring Data JPA repositories
└── service/      Account and conversation rules
src/main/resources/
├── application.properties
└── static/       Login, search, group, and chat UI
```

## Run locally

You need JDK 21, Docker with Compose, and the Maven Wrapper.

Start PostgreSQL:

```powershell
docker compose up -d database
```

Then start the app on Windows:

```bat
mvnw.cmd spring-boot:run
```

Open <http://localhost:8080>. The local database credentials in `compose.yaml` are for development only.

To run both the app and PostgreSQL in Docker:

```powershell
docker compose up --build
```

Stop the services with `docker compose down`. PostgreSQL data is kept in a named volume. **Do not** use `docker compose down -v` unless you intend to permanently delete local accounts and messages.

## Configuration and deployment

The application reads configuration from environment variables:

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/classchat` | JDBC URL for PostgreSQL |
| `DATABASE_USERNAME` | `classchat` | Database user |
| `DATABASE_PASSWORD` | `local-dev-only` | Development database password |
| `MAIL_HOST` | unset | SMTP server hostname; required for account emails |
| `MAIL_PORT` | `587` | SMTP server port |
| `MAIL_USERNAME` | unset | SMTP account username, if required |
| `MAIL_PASSWORD` | unset | SMTP account password, if required |
| `MAIL_FROM` | unset | Sender email address; required for account emails |
| `MAIL_SMTP_AUTH` | `true` | Whether SMTP authentication is enabled |
| `MAIL_SMTP_STARTTLS` | `true` | Whether SMTP STARTTLS is enabled |
| `VAPID_PUBLIC_KEY` | unset | Base64url-encoded uncompressed P-256 public key |
| `VAPID_PRIVATE_KEY` | unset | Base64url-encoded P-256 private key; keep secret |
| `VAPID_SUBJECT` | `mailto:admin@classchat.local` | Contact URI identifying the push sender |

Use your email provider's SMTP settings and store credentials in environment variables, not source control. To generate a VAPID key pair with Node.js:

```powershell
node -e "const {createECDH}=require('node:crypto'); const e=createECDH('prime256v1'); e.generateKeys(); console.log('VAPID_PUBLIC_KEY='+e.getPublicKey('base64url')); console.log('VAPID_PRIVATE_KEY='+e.getPrivateKey('base64url'))"
```

Set both generated keys and replace `VAPID_SUBJECT` with a valid `mailto:` address or HTTPS URL. Web Push is optional; without keys the app works normally but does not offer notification setup. Service workers and browser push require HTTPS, except on localhost.

For deployment, use a managed PostgreSQL database and set all database variables to its credentials. Keep secrets out of source control. Serve the app over HTTPS; the browser automatically uses secure WebSockets (`wss://`) when the page is HTTPS.

Build and run the executable JAR:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target/classchat.jar
```

Build the Docker image:

```powershell
docker build -t classchat .
```

The application container still needs a reachable PostgreSQL database and the database environment variables above.

## Tests

```powershell
.\mvnw.cmd test
```

Tests use an in-memory H2 database and a recording email sender. They cover password hashing and reset, email verification, unread counts, conversation persistence and membership, history pagination, and authenticated WebSocket delivery.

## Main API

- `POST /api/auth/register` — create an account and send a verification code.
- `POST /api/auth/verify-email` and `POST /api/auth/resend-verification`
- `POST /api/auth/password-reset/request` and `POST /api/auth/password-reset/confirm`
- `POST /api/auth/login` — sign in with email or username.
- `POST /api/auth/logout`, `GET /api/auth/me`
- `GET /api/users?handle=...` — search users by username.
- `GET /api/conversations`
- `POST /api/conversations/direct`
- `POST /api/conversations/groups`
- `GET /api/conversations/{id}/messages?beforeId=...&limit=50`
- `POST /api/conversations/{id}/read` — mark the conversation read through its latest message.
- `POST /api/conversations/{id}/members` and `DELETE /api/conversations/{id}/members/{handle}` — creator-only group management.
- `GET /api/push/config`, `POST /api/push/subscriptions`, and `DELETE /api/push/subscriptions`

State-changing HTTP requests require the CSRF token returned by `GET /api/auth/csrf`. The browser client handles this automatically.
