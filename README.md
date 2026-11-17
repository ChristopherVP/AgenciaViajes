# 🚌 RutaViva — Agencia de Viajes (Intercity Bus Travel Agency)

**A desktop system for selling and operating intercity bus trips across Ecuador, written in Java (Swing) on top of an Oracle database. It covers passenger ticket sales with seat maps, a prepaid wallet, driver dispatch with real-time trip simulation, driver payouts, invoicing, role-based administration, and PDF / Excel reporting.**

![Java](https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk)
![Swing](https://img.shields.io/badge/UI-Java%20Swing-blue)
![Oracle](https://img.shields.io/badge/Database-Oracle-F80000?logo=oracle&logoColor=white)
![NetBeans](https://img.shields.io/badge/IDE-NetBeans%20%2F%20IntelliJ-1B6AC6?logo=apachenetbeanside)
![Status](https://img.shields.io/badge/Status-Educational%20project-green)

RutaViva is a university project that simulates a travel agency that sells bus tickets for several transport companies. Three kinds of people use it:

- **Passengers** register, top up a wallet, pick a trip, choose their seats on a seat map, pay from the wallet, follow the bus live on a map and download their tickets and invoices as PDF.
- **Drivers** register with their documents and a debit card, accept the trips the administration assigns to them, report incidents and receive **80 %** of what each trip collects.
- **Administrators** (with different job titles and permissions) manage companies, buses, routes, trips, drivers, passengers, reservations, invoices, wallet top-ups, driver payments and complaints, and export formal reports.

The domain follows a layered object-oriented design (business objects, persistence, controllers, facade, GUI). **Everything is stored in Oracle through JDBC**, with a tiny connection pool and automatic schema creation. PDF and Excel files are generated **without third-party libraries**; the only external dependency is the Oracle JDBC driver.

---

## Table of Contents

1. [Features at a Glance](#-features-at-a-glance)
2. [How the System Works (Business Rules)](#-how-the-system-works-business-rules)
3. [Project Requirements](#-project-requirements)
4. [Installation and Setup](#-installation-and-setup)
5. [Running the Application](#-running-the-application)
6. [First Start and Initial Data](#-first-start-and-initial-data)
7. [Resetting the System](#-resetting-the-system)
8. [User Guide](#-user-guide)
9. [Administration: Roles, Job Titles and Permissions](#-administration-roles-job-titles-and-permissions)
10. [Security](#-security)
11. [Validation Rules](#-validation-rules)
12. [Domain Model](#-domain-model)
13. [Persistence and Oracle](#-persistence-and-oracle)
14. [Database Schema](#-database-schema)
15. [Reports and Exports](#-reports-and-exports)
16. [Project Structure](#-project-structure)
17. [Architecture and Design Notes](#-architecture-and-design-notes)
18. [Known Limitations](#-known-limitations)
19. [Troubleshooting](#-troubleshooting)
20. [Author, Repository and License](#-author-repository-and-license)

---

## ✨ Features at a Glance

**Accounts and access**
- Self-registration for **passengers** and **drivers**, with strict validation (Ecuadorian *cédula* check digit, allowed e-mail domains, `09` mobile numbers, adult age, password policy).
- Login with **temporary lock** after repeated failures, **forced password change** for temporary passwords, and **password recovery** through a personal security question.
- Profile screen with photo (PNG/JPG, cropped to a square), contact data, password and security-question changes.
- Three portals chosen automatically at login: passenger portal, driver portal and administration window.

**Passengers**
- Browse upcoming trips with **origin / destination filters**, see the route on a map and pick seats on an interactive **seat map** (window / aisle).
- Buy up to **5 seats** per purchase. Automatic **50 % fare** for children, teenagers, seniors and people with disabilities.
- **Wallet** (prepaid balance): instant top-up by card, or cash top-up approved by finance; full movement history.
- **My tickets**, **my invoices**, PDF download of every ticket, invoice and wallet movement.
- **Live tracking**: the bus moves on the map between departure and arrival time.
- Send complaints, suggestions or payment problems to the administration from *My profile*.

**Drivers**
- Registration with company choice, license data, debit card and uploaded documents; the account stays **PENDING** until an administrator approves it.
- See the bus and route assigned to them, the trips scheduled for them, and **accept / reject** assigned trips.
- Notices (notifications) from the administration, **incident reports** (mechanical failure, route incident, cannot travel…) and **cancellation requests**.
- *My payments*: registered debit card and the 80 % earned per completed trip, plus invoices.

**Administration**
- Tabs shown according to the job title: **Companies, Users, Drivers, Passengers, Buses, Routes, Trips, Reservations, Invoices, News (Novedades), Top-ups, Fleet and dispatch, Driver payments**.
- **Users tab** with filter buttons: *All / Administrators / Passengers / Drivers* (people who have a profile but no account are listed too, as "SIN CUENTA").
- **Dispatch**: see where each driver is and assign the next trip *from where the previous one ends*.
- **Live search** in every tab, buttons that modify data are hidden where the job title is read-only.
- Informal and formal **PDF reports**, per-period trip report (PDF / Excel), “save current tab as PDF / Excel”.
- Special queries and a statistics screen; built-in user manual.

**Money**
- One invoice (*comprobante*) per ticket purchase, per wallet top-up, per driver payment and per refund, numbered `001-001-000000001`.
- Invoice + invoice-detail + payment records are also stored for every confirmed reservation (tables `FACTURAS`, `DETALLES_FACTURA`, `PAGOS`).
- **Refunds with credit note** when a trip is cancelled.

**Maintenance and quality**
- Automatic creation and versioned migration of the Oracle schema.
- One-click **reset tool** (`RestablecerSistema`) and a **connection test** tool (`ProbarConexion`).
- Real-time **simulation clock** that departs and finishes trips automatically.
- Splash screen, loading dialogs, custom vector icons drawn with Java2D (no image files needed).

---

## 📐 How the System Works (Business Rules)

### Trip life cycle

```
Por aceptar ──► Programado ──► En Curso ──► Finalizado ──► Completado
     │              │
     └──────────────┴────────► Cancelado
```

| State | Meaning |
|-------|---------|
| **Por aceptar** | The administration assigned the trip to a driver. **Nobody can buy it yet.** |
| **Programado** | The driver accepted it (a valid registered debit card is required). It goes on sale and the driver’s payment appears as *PROGRAMADA*. |
| **En Curso** | Departure time reached: the bus starts moving on the live map. Sales are closed. |
| **Finalizado** | Arrival time reached: the driver payment becomes *PENDIENTE* with its final amount. |
| **Completado** | Finance paid the driver (*PAGADA*) and the invoice was issued. |
| **Cancelado** | Cancelled: every paid ticket is refunded to the passenger’s wallet with a credit note and the driver payment is cancelled. |

A **simulation clock** runs in the background (first run 20 s after start, then every 15 s). It applies those state changes by itself, including **auto-cancelling** a trip that the driver did not accept before departure.

### Dispatch rules
- A driver approved by an administrator starts **without bus or route**; no trip can be scheduled for him until both are assigned.
- **One bus belongs to one driver**; a route can be shared by several drivers.
- A new trip must depart **at least 15 minutes** from now, so the driver can still accept it.
- **Continuity rule:** a driver’s next trip must depart from the city where the previous one ends.
- A bus (and a driver) needs **30 minutes of rest** between two trips; overlapping trips are rejected.
- A driver can **reject** a trip he has not accepted yet (it is cancelled and the administration must assign another one). An **accepted** trip can only be cancelled if the administrator approves the driver’s cancellation request.
- If a bus breaks down before departure, the administrator can **replace the bus** (the broken one becomes INACTIVE); if there is no replacement the trip can be cancelled with refunds.

### Buying a ticket
1. The passenger chooses a trip and 1–5 seats (**maximum 5 seats per purchase**).
2. The price is the trip price (or the route price) × seats, with a **50 % factor** for the passenger types `NIÑO` (< 12), `ADOLESCENTE` (12–17), `TERCERA_EDAD` (> 65) and `DISCAPACITADO`.
3. Sales close **30 minutes before departure** and when the bus has left or has no seats left.
4. The price is debited from the **wallet**; if the balance is not enough the purchase is refused and the passenger must top up.
5. The reservation is created as **CONFIRMADA** and the seats become occupied. If any step fails, everything done before is undone.
6. **A purchased ticket is final: it cannot be cancelled or refunded by the passenger.** Refunds only happen when the agency cancels the trip.

### Wallet and top-ups
- Amount from **$1.00 to $500.00**, at most two decimals, at most **3 pending requests** per passenger.
- **Card top-up:** validated (Luhn number, expiry, CVV) and credited instantly; only the card brand and the **last 4 digits** are kept. An invoice is issued immediately.
- **Cash top-up:** the passenger enters a 6–12 digit payment-receipt number; the request stays *PENDIENTE* until finance **approves** it (invoice issued) or **rejects** it with a reason.
- The balance is calculated from the approved movements (top-ups, purchases, refunds).

### Driver payouts
- The driver earns **80 %** of the money collected from tickets of the trip; the other **20 %** is the platform margin.
- Payout states: `PROGRAMADA` → `PENDIENTE` → `PAGADA` (or `CANCELADA`).
- Finance pays the driver’s registered **debit card** from *Pagos a conductores*; a payout invoice is issued.
- The card is validated like a real one (bank from the Ecuadorian list, Luhn, known debit brand, valid expiry and **holder name equal to the driver’s name**).

### News (Novedades)
- Drivers report: *cannot travel, mechanical failure, route incident, complaint, other*. Passengers write from *My profile*: *complaint, suggestion, payment problem, other*.
- States: `ABIERTA` → `EN_REVISION` → `RESUELTA`, with an administrator’s answer. A person can have at most **5 open** reports.

---

## 📋 Project Requirements

### Functional requirements

| ID | Requirement |
|----|-------------|
| FR-01 | Register passengers and drivers; reject duplicate username, cédula and e-mail. |
| FR-02 | Validate the Ecuadorian cédula (10 digits, province 01–24 or 30, third digit < 6, modulus-10 check digit). |
| FR-03 | Restrict account e-mails to lowercase and to the allowed provider list. |
| FR-04 | Authenticate by username and password, lock logins after repeated failures, and force a change of temporary passwords. |
| FR-05 | Recover a forgotten password through the account’s security question, with a lock after repeated failures. |
| FR-06 | Create the main administrator automatically on first start; let it create administrators and operators with a job title. |
| FR-07 | Manage companies, buses, routes, drivers, passengers and trips (create, edit, delete, search). |
| FR-08 | Assign one bus and one route to each driver; schedule trips only for drivers that have them. |
| FR-09 | Prevent a bus or a driver from being in two trips at once; enforce rest time and route continuity. |
| FR-10 | Let drivers accept or reject assigned trips; auto-cancel trips that are not accepted before departure. |
| FR-11 | Sell tickets with seat selection, price rules by passenger type, and payment from the wallet. |
| FR-12 | Keep a wallet per passenger with card and cash top-ups, approval workflow and movement history. |
| FR-13 | Run trips in real time (depart, move on a map, arrive) and close them automatically. |
| FR-14 | Compute and pay driver earnings (80 %), with scheduled, pending, paid and cancelled states. |
| FR-15 | Cancel trips with automatic refunds and credit notes. |
| FR-16 | Issue and store invoices for tickets, top-ups, driver payments and refunds; let each person see only their own. |
| FR-17 | Store an invoice, its detail lines and its payment for every confirmed reservation. |
| FR-18 | Receive and answer news (reports, complaints, suggestions) from drivers and passengers. |
| FR-19 | Show each administrator only the modules and actions allowed by his job title. |
| FR-20 | Export PDF receipts, formal PDF reports, per-period trip reports (PDF / Excel) and any table (PDF / Excel). |
| FR-21 | Create the Oracle schema automatically and load initial data once. |
| FR-22 | Provide a maintenance tool that restores the system to a fresh-install state. |

### Non-functional requirements

| ID | Requirement |
|----|-------------|
| NFR-01 | **Desktop GUI** built with Java Swing. |
| NFR-02 | **Object-oriented, layered design** (GUI → controllers → facade/DAO → Oracle). |
| NFR-03 | **All SQL uses `PreparedStatement`**; multi-step writes can run in one transaction. |
| NFR-04 | **No secrets in the code:** database credentials in `oracle.properties` or environment variables; admin password from an environment variable. |
| NFR-05 | **Passwords and security answers are hashed** (PBKDF2-HMAC-SHA256 with salt). |
| NFR-06 | **No extra libraries:** PDF and `.xlsx` files are written by the project itself. |
| NFR-07 | **Fast start-up:** one schema-version query on a normal start; first connection opened in the background. |
| NFR-08 | **Language:** user interface and messages are in **Spanish**. |

### Software requirements

| Requirement | Details |
|-------------|---------|
| **JDK** | **JDK 21 or newer** (the code uses records and pattern matching). The IDE configuration of the project targets JDK 25/26. |
| **IDE** *(recommended)* | **Apache NetBeans** (Ant project in `nbproject/`) or **IntelliJ IDEA** (`AgenciaViajes.iml`, `.idea/`). |
| **Dependency** | `lib/ojdbc11-23.5.0.24.07.jar` (Oracle JDBC driver, already included in `lib/`). |
| **Database** | **Oracle Database** (developed against Oracle XE, pluggable database `XEPDB1`). A schema/user with permission to create tables. |
| **OS** | Any OS with a JDK and a graphical environment (Windows, Linux, macOS). |
| **Display** | Recommended ≥ 1200 × 700 px (the main window opens maximized). |

---

## 🔧 Installation and Setup

```bash
# 1. Clone the repository
git clone https://github.com/ChristopherVP/AgenciaDeViajes.git
cd AgenciaDeViajes
```

### 1. Create the Oracle user (schema)

Connect as a privileged user (for example `SYSTEM`) to your pluggable database and create the schema owner. Replace the password with your own:

```sql
ALTER SESSION SET CONTAINER = XEPDB1;

CREATE USER AGENCIA_VIAJES IDENTIFIED BY "your_password_here";
GRANT CONNECT, RESOURCE TO AGENCIA_VIAJES;
ALTER USER AGENCIA_VIAJES QUOTA UNLIMITED ON USERS;
```

> You do **not** need to run any table script: the application creates and versions every table by itself at start-up (see [Persistence and Oracle](#-persistence-and-oracle)).

### 2. Configure the connection

Create a file named `oracle.properties` in the folder you run the program from (the project root):

```properties
url=jdbc:oracle:thin:@//localhost:1521/XEPDB1
user=AGENCIA_VIAJES
password=your_password_here
```

Or use environment variables (they take priority over the file):

| Variable | Meaning |
|----------|---------|
| `ORACLE_URL` | JDBC URL |
| `ORACLE_USER` | Database user |
| `ORACLE_PASSWORD` | Database password |

> ⚠️ `oracle.properties` contains credentials. It is listed in `.gitignore`: **never commit it.**

### 3. Test the connection

Run the class `DB.ProbarConexion`. It prints the Oracle version and the connected user, or the exact error code if it cannot connect.

### 4. Optional: administrator credentials

The first time the system starts it creates the **main administrator**. Its data comes from environment variables:

| Variable | Meaning | Default |
|----------|---------|---------|
| `RUTAVIVA_ADMIN_USUARIO` | Main administrator username | `admin` |
| `RUTAVIVA_ADMIN_PASSWORD` | Initial password | `CambiarAdmin1` (temporary) |
| `RUTAVIVA_CLAVE_DEMO` | Initial password of the sample accounts (drivers, passengers, managers, operators) | `CambiarAdmin1` (temporary) |

If you do not define them, the first login is **`admin` / `CambiarAdmin1`**, and the system **forces a password change** at that first login.

> ⚠️ The default password is meant for development and classroom use only. For anything else, set your own values **before the first run**.

---

## ▶️ Running the Application

**From NetBeans or IntelliJ (recommended):** open the project, let the IDE load `lib/ojdbc11-23.5.0.24.07.jar`, and run the class **`main.Main`**.

**From the command line** (run it from the project root so `oracle.properties`, `fotos/` and `documentos/` are found):

```bash
# Linux / macOS
javac -encoding UTF-8 -d out -cp lib/ojdbc11-23.5.0.24.07.jar $(find src -name "*.java")
java -cp "out:lib/ojdbc11-23.5.0.24.07.jar" main.Main
```

```bat
:: Windows
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out -cp lib\ojdbc11-23.5.0.24.07.jar @sources.txt
java -cp "out;lib\ojdbc11-23.5.0.24.07.jar" main.Main
```

What `main.Main` does: installs a global error handler, **pre-warms the Oracle connection in the background**, applies the look and feel, shows the splash screen, opens the login window, and starts the **simulation clock** that advances trips every 15 seconds.

---

## 🌱 First Start and Initial Data

On every start `InicializadorSistema` prepares the system as fast as possible and **never deletes anything that already exists**:

1. **Schema:** one query (`SELECT MAX(VERSION) FROM ESQUEMA_VERSION`). The very first time it creates all tables; later it only applies new migrations.
2. **Main administrator:** created if it does not exist.
3. **Initial data (only once):** loaded the first time and marked in the `CONFIGURACION` table, so it is never loaded twice and your own data is never overwritten.

**What gets loaded**

| Block | Content |
|-------|---------|
| **Companies** | *Flota Andina* (Quito), *Costa Express* (Guayaquil), *Transportes Cumbre* (Cuenca). |
| **Buses** | 3 per company (40 / 44 / 48 seats), several brands (Mercedes-Benz, Volvo, Scania, Hino). |
| **Routes** | 4 per company between Ecuadorian cities (Quito–Ambato, Quito–Cuenca, Guayaquil–Manta, Cuenca–Loja…), with distance, price, duration and intermediate stops. |
| **Drivers** | 2 per company, each with a fixed bus and route, and a sample debit card. |
| **Trips** | 8 per company, one per day starting tomorrow, alternating outbound and return. Seats are generated for each trip. |
| **Passengers** | 24 sample passengers (varied ages, so child / teen / senior fares apply), each with an approved wallet top-up and its invoice. |
| **Accounts** | One *Supervisor de operaciones* administrator and one *Operador de empresa* per company, one account per driver, and one account per adult passenger. All start with a temporary password and must change it at first login. |
| **Reservations** | Real purchases made with the normal buying flow (the wallet is topped up when needed). |
| **Invoices, details and payments** | One invoice + detail line + payment for every confirmed reservation. |
| **News** | Five sample reports from passengers and drivers in different states. |

The sample accounts’ passwords come from `RUTAVIVA_CLAVE_DEMO` (or the temporary default). Sample logins look like `gerente1`, `operador1`, `cmendoza` (first letter of the first name + first surname) or the first part of the passenger’s e-mail.

> Minors in the sample data have a profile but **no account** (registration requires 18+). They appear in the *Users* tab as `SIN CUENTA`.

---

## 🧹 Resetting the System

Run the separate maintenance class **`mantenimiento.RestablecerSistema`** (right-click → *Run File* in NetBeans). It is **not** part of the normal application.

A confirmation window appears and you must type `BORRAR`; anything else cancels and nothing is deleted.

| What happens | Detail |
|--------------|--------|
| **Deleted** | Every row of every table (users, passengers, drivers, reservations, trips, invoices, wallets, payouts, notices, news, cards…), the configuration marks, and the `fotos/` and `documentos/` folders. This includes any company, bus, route or driver you added. |
| **Recreated** | The tables, the main administrator and the initial data, exactly as in the first start. |
| **After the reset** | Log in as `admin` / `CambiarAdmin1` (or your environment values); the password must be changed at first login. |

> ⚠️ The reset is **irreversible**. Close the application first.

---

## 🧭 User Guide

### Passenger
1. **Register:** on the login screen choose *Create account*, select **“Soy pasajero”** and fill in the form (cédula, name, e-mail, mobile, birth date, username, password, security question and answer).
2. **Log in** and the **passenger portal** opens with four tabs:
   - **Comprar pasaje** – filter by origin/destination, *Ver ruta en el mapa*, choose seats on the seat map and press *Comprar pasaje*.
   - **Mi billetera** – balance, *Solicitar recarga* (card or cash) and the movement history with *Descargar PDF del movimiento*.
   - **Mis pasajes** – your tickets, *Ver factura*, *Descargar pasaje PDF* and live tracking of your bus.
   - **Mis facturas** – every invoice you were issued, searchable.
3. **My profile** (account menu): photo, contact data, password, security question, and a form to write to the administration (complaint, suggestion, payment problem).

### Driver
1. **Register** as **“Soy conductor”**: choose the company, license type (D or E), license number and expiry, debit card, and upload the required documents.
2. Wait for the administrator to **approve** the account (your status is *PENDIENTE* until then).
3. In the **driver portal**: *Mis viajes* (accept / reject assigned trips, notify a completed trip), *Avisos*, *Reportar al administrador*, *Mis reportes*, *Mis pagos* (card and earnings) and *Mis facturas*.

### Administrator
1. Log in with an administrator or operator account; the window shows only the tabs your job title allows.
2. **Operators** must first choose a company in the *Empresas* tab; the other tabs unlock afterwards and work only on that company.
3. Typical flows:
   - **Approve a driver:** *Usuarios* → select the pending driver → *Revisar solicitud* → review documents → approve; then assign a bus and route in *Conductores*.
   - **Assign a trip:** *Flota y despacho* → select the driver → *Asignar viaje desde su ubicación*.
   - **Approve a cash top-up:** *Recargas* → *Aprobar* / *Rechazar*.
   - **Pay a driver:** *Pagos a conductores* → select a pending payout → *Pagar al conductor*.
   - **Answer a report:** *Novedades* → select → answer / resolve.
4. **Menus:** *Consultas* (special queries), *Reportes* (statistics, formal PDF reports, period report, save current tab as PDF/Excel), *Ayuda* (user manual, about) and *Cuenta* (my profile, log out).

---

## 🛡 Administration: Roles, Job Titles and Permissions

There are four **roles**: `Administrador`, `Operador`, `Conductor` and `Pasajero`. Administrators and operators have a **job title (cargo)** that decides what they can *see* and what they can *change*.

| Job title | Can see | Can add / edit / approve |
|-----------|---------|--------------------------|
| **Administrador principal** | Everything | Everything (the only one who creates administrators) |
| **Gerente general** | Everything | Companies, users, drivers, buses, routes, trips, news, reports |
| **Supervisor de operaciones** | Companies, users, drivers, buses, routes, trips, reservations, news, reports | Users, drivers, buses, trips, news |
| **Coordinador de rutas y viajes** | Companies, buses, routes, trips, reservations, reports | Routes, trips |
| **Jefe de finanzas y recargas** | Companies, reservations, invoices, top-ups, driver payments, reports | Top-ups, driver payments |
| **Atención al cliente** | Passengers, reservations, invoices, news, top-ups | Passengers, news |
| **Operador** (any operator title) | Companies, drivers, buses, routes, trips, reservations, news — **only of one company** | Buses, routes, trips, drivers, news |

Where a title is read-only, the buttons that modify data are hidden. **Reservations** and **Invoices** are always read-only (a purchase is final).

---

## 🔐 Security

| Area | Implementation |
|------|----------------|
| **Password hashing** | **PBKDF2-HMAC-SHA256** with a random salt, JDK only. Stored as `iterations$salt$hash`. Security answers are hashed the same way. |
| **No secrets in code** | Database credentials: `oracle.properties` (git-ignored) or `ORACLE_*` variables. Admin and sample passwords: `RUTAVIVA_*` variables. |
| **Forced password change** | Temporary passwords (main admin, collaborators, sample accounts) must be changed before entering. |
| **Login throttling** | After **5 failed logins** the account is locked for **2 minutes**. |
| **Recovery throttling** | After **5 failed recovery answers** recovery is locked for **15 minutes**; messages never reveal whether an account exists. |
| **SQL injection** | Every query uses `PreparedStatement` (`DB.Bd`). |
| **Card data** | Only the brand, holder, expiry and **last 4 digits** are stored — never the full number or the CVV. |
| **Permissions** | Every administrator action is checked against the job title (`Permisos`); each passenger / driver only sees their own invoices and reports. |
| **Uploaded files** | Profile photos: PNG/JPG, max 8 MB, cropped and stored as 256 px PNG. Driver documents are checked and kept in `documentos/<cédula>/`. |

---

## ✅ Validation Rules

| Field | Rule |
|-------|------|
| **Cédula** | 10 digits, numeric, province 01–24 (or 30 for Ecuadorians abroad), third digit < 6, valid modulus-10 check digit; unique. The province is detected from the first two digits. |
| **RUC** (companies) | 13 digits, numeric, province 01–24, establishment number not `000`. |
| **Username** | 4–20 characters: letters, digits, dot, hyphen or underscore; starts with a letter or digit; unique. |
| **E-mail** | Lowercase, valid format, max 50 characters, provider must be one of: `gmail.com, outlook.com, hotmail.com, live.com, msn.com, yahoo.com, yahoo.es, yahoo.mx, icloud.com, me.com, mac.com, proton.me, protonmail.com, zoho.com, zohomail.com, gmx.com, gmx.net, aol.com, uce.edu.ec`. |
| **Name** | Letters and spaces only, name **and** surname, 5–50 characters. |
| **Mobile phone** | Exactly 10 digits starting with `09`. |
| **Birth date** | `dd/mm/yyyy`, real calendar date, not in the future, **age 18–120** for accounts. |
| **Password** | 8–40 characters, with uppercase, lowercase and a digit, no spaces. A strength indicator is shown while typing. |
| **Security question** | 10–80 characters; the answer is stored hashed. |
| **Driver license** | Professional type **D or E**, number of 6–12 letters/digits, valid at least 30 more days and at most 10 years ahead. |
| **Debit card** | Bank from the Ecuadorian list, Luhn-valid number (13–19 digits), valid expiry, holder equal to the driver’s name. |
| **Wallet top-up** | $1.00–$500.00, max 2 decimals, max 3 pending; cash receipt number of 6–12 digits and not reused. |
| **Ticket purchase** | 1–5 seats, no repeated seats, trip on sale, enough balance, no second active reservation on the same trip. |
| **Trips** | Valid state, bus and driver free (with 30 min rest), driver with assigned bus and route, continuity respected, departure ≥ 15 min ahead. |
| **Buses / routes** | Buses: positive capacity, plate, brand and state (`ACTIVO` / `INACTIVO`) are required. Routes: state `ACTIVA` / `INACTIVO`. |

---

## 🧩 Domain Model

**Business objects (`objetosNegocio`)**

| Class | Responsibility |
|-------|----------------|
| `Usuario` / `Rol` / `Cargo` | Account with role, job title, state (`ACTIVO`, `INACTIVO`, `PENDIENTE`), company, security data and pending-password-change flag. |
| `Empresa` | Transport company (name, RUC, address, phone, e-mail). |
| `Bus` | Bus with plate, chassis, engine, brand, year, capacity, state and company. |
| `Ruta` | Direct route between two cities with distance, price, estimated hours, stops and warnings. |
| `Viaje` | A scheduled trip: bus, route, dates and hours, available seats, price, state and up to two drivers. |
| `Asiento` | Seat of a trip (`Disponible`, `Reservado`, `Ocupado`) with its location (window / aisle). |
| `Pasajero` | Passenger profile with birth date and disability flag; computes age and passenger type (`NIÑO`, `ADOLESCENTE`, `ADULTO`, `TERCERA_EDAD`, `DISCAPACITADO`). |
| `Conductor` | Driver profile (name, cédula, phone, license, state, company). |
| `Reserva` | Reservation / ticket: passenger, trip, seats, price, state, payment method. |
| `Movimiento` | Wallet movement (`RECARGA`, `COMPRA`, `REEMBOLSO`) with method and state (`PENDIENTE`, `APROBADA`, `RECHAZADA`). |
| `Comprobante` | Issued invoice (`PASAJE`, `RECARGA`, `LIQUIDACION`, `REEMBOLSO`). |
| `Factura` / `DetalleFactura` / `Pago` | Invoice of a reservation, its detail lines and its payment. |
| `Liquidacion` | Driver payout (`PROGRAMADA`, `PENDIENTE`, `PAGADA`, `CANCELADA`); constant 80 % for the driver. |
| `Novedad` | Report, complaint or suggestion with state and answer. |
| `TarjetaGuardada` | Minimal card data (brand, last 4 digits…) for charging (`COBRO`) or topping up (`RECARGA`). |
| `PeriodoInforme` | Fortnight / month / year ranges for the period report. |

**Controllers (`control`)**

| Class | Responsibility |
|-------|----------------|
| `ControladorUsuarios` | Registration, login, recovery, profile, creation / approval / deletion of accounts. |
| `ControladorCompras` | Quote and buy tickets (debit, confirm, invoice). |
| `ControladorReservas` | Reservation rules, seat assignment, price calculation. |
| `ControladorBilletera` | Wallet balance, top-up requests, approvals, debits and refunds. |
| `ControladorViajes` | Create / update / cancel trips; bus availability checks; refunds. |
| `ControladorAsignaciones` | Bus and route of each driver; continuity rule. |
| `ControladorDespacho` | Dispatch: assign, accept, reject, cancellation requests, bus replacement. |
| `ControladorSimulacion` | Real-time trip states (departure, movement, arrival, auto-cancel). |
| `ControladorLiquidaciones` | Driver payouts. |
| `ControladorFacturacion` | Invoice book and numbering. |
| `ControladorTarjetas` | Driver debit card validation and storage. |
| `ControladorNovedades` / `ControladorNotificaciones` | Reports and internal notices. |
| `ControladorPortalConductor` / `ControladorEmpresas` / `ControladorConductores` | Driver queries and company / driver management. |
| `ControladorFotos` | Profile photos. |
| `GeneradorInformes` / `InformeViajesPeriodo` / `ComprobantesPdf` | Formal reports, period report and per-operation PDFs. |
| `InicializadorSistema` / `DatosIniciales` / `DatosComplementarios` / `DatosDemostracion` | Start-up preparation and initial data. |
| `ResultadoOperacion` | Uniform result (success, message, data) returned to the GUI. |

**Services and utilities (`objetosServicio`, `excepciones`, `interfaces`, `fachadas`)**

| Class | Responsibility |
|-------|----------------|
| `Permisos` | Which module each job title can see / edit. |
| `Seguridad` | PBKDF2 password hashing. |
| `ConfiguracionAdmin` | Main-administrator settings from environment variables. |
| `Bancos` / `DocumentosConductor` / `Fecha` / `Periodo` / `Marca` | Bank list, driver documents, date value object, periods and brand constants. |
| `InformePdf` | Dependency-free PDF builder (cover, index, numbered sections, tables, bar charts, A4, Helvetica). |
| `Validador` / `PersistenciaException` / `FachadaException` | Validation rules and typed exceptions. |
| `IFachada` / `FachadaArchivos` | Facade used by the controllers to reach every DAO. |

---

## 💾 Persistence and Oracle

Everything lives in Oracle. There are no data files left to manage.

| Piece | What it does |
|-------|--------------|
| `DB.ConexionOracle` | Small **connection pool** (up to 4 reusable connections). `close()` returns the connection to the pool; stale connections are re-checked; the first one is opened in the background at start-up. |
| `DB.EsquemaOracle` | **Versioned migrations**. `ESQUEMA_VERSION` stores the applied versions. Migration 1 creates all tables, migration 2 adds indexes. To change the model, add a **new** migration at the end — never edit migration 1. |
| `DB.Bd` | JDBC helpers: queries with parameters (always prepared), transactions, batch inserts, date conversion, safe text reading and **sequential codes** (`EMP001`, `VIA002`…). Duplicate codes are retried automatically. |
| `persistencia.*` | One DAO per table (`Empresas`, `Buses`, `Rutas`, `Conductores`, `Pasajeros`, `Usuarios`, `Viajes`, `Reservas`, `Asientos`, `Facturas`, `DetallesFactura`, `Pagos`, `Movimientos`, `Novedades`). |
| `persistencia.AlmacenTexto` | Historical name: it was a text file with one record per line; now each “file” is an Oracle table (`asignaciones.dat → ASIGNACIONES`, `notificaciones.dat → NOTIFICACIONES`, `liquidaciones.dat → LIQUIDACIONES`, `tarjetas.dat → TARJETAS`, `facturas.dat → COMPROBANTES`). |
| `.dat` names | Constructors such as `new Empresas("empresas.dat")` keep a legacy argument that is **ignored**; it is only there so the controllers did not need to change. |

Oracle stores an empty text as `NULL`; `Bd` converts empty strings to `NULL` when saving and back to `""` when reading, so the application never sees a `null` text.

---

## 🗄 Database Schema

Created automatically at start-up (migration 1). `ESQUEMA_VERSION` is added on top of these.

| Table | Purpose |
|-------|---------|
| `EMPRESAS` | Transport companies. |
| `BUSES` | Fleet (FK → company). |
| `RUTAS` | Routes (FK → company). |
| `CONDUCTORES` | Driver profiles (FK → company). |
| `PASAJEROS` | Passenger profiles (birth date, disability flag). |
| `USUARIOS` | Accounts: role (`TIPO`), job title, state, hashed password and answer, lock counters, pending-password flag, link to a profile (`ID_PERFIL`). Unique `LOGIN`. |
| `VIAJES` | Trips (FK → bus, route, company, up to two drivers). |
| `RESERVAS` | Tickets (FK → passenger, trip, company). |
| `ASIENTOS` | Seats of each trip (deleted with the trip: `ON DELETE CASCADE`). |
| `FACTURAS` | Invoice of each reservation, with company / passenger snapshots. |
| `DETALLES_FACTURA` | Invoice lines (`ON DELETE CASCADE`). |
| `PAGOS` | Payment of each invoice. |
| `MOVIMIENTOS` | Wallet movements (FK → passenger). |
| `NOVEDADES` | Reports, complaints and suggestions. |
| `ASIGNACIONES` | Bus and route per driver (one bus → one driver). |
| `NOTIFICACIONES` | Internal notices (to a driver id or `ADMIN`). |
| `LIQUIDACIONES` | Driver payouts. |
| `TARJETAS` | Minimal card data per owner and use (`COBRO` / `RECARGA`). |
| `COMPROBANTES` | Invoice book (tickets, top-ups, payouts, refunds). |
| `CONFIGURACION` | Key / value marks (for example which initial data blocks were already loaded). |

Indexes cover the most frequent look-ups: trips by driver, state and date; reservations by trip and passenger; seats by trip; wallet movements by passenger; notices by destination; invoices by date.

---

## 📄 Reports and Exports

| Output | How to get it |
|--------|---------------|
| **Ticket / invoice / wallet movement / driver payment PDF** | “Descargar PDF” buttons in the portals and in *Facturas* (`ComprobantesPdf`). |
| **Formal PDF report** | *Reportes → Informes formales en PDF…*: one button per section (routes only, buses only…), a custom selection, or the complete system report. Only the sections your job title can see appear. |
| **Period trip report (PDF / Excel)** | *Reportes → Informe de viajes por periodo…*: fortnight, month or year, with trips by state, passengers carried, money collected, driver payments, platform margin and trip-by-trip detail. An operator only sees his company. |
| **Current tab to PDF / Excel** | *Reportes → Guardar la pestaña actual…*: exports what the table shows, including the active search. |
| **Statistics and special queries** | *Reportes → Reportes y Estadísticas* and *Consultas → Consultas Especiales*. |

The formal PDF has a cover, an index with page numbers, numbered sections, indicators, tables that continue on the next page and bar charts. `.xlsx` files are built as a ZIP of XML parts; PDFs are written directly with the standard Helvetica font. **No external libraries are used.**

---

## 🧱 Project Structure

```text
AgenciaViajes/
├── lib/ojdbc11-23.5.0.24.07.jar            # Oracle JDBC driver
├── oracle.properties                       # DB credentials (local only, git-ignored)
├── build.xml / nbproject/                  # NetBeans (Ant) project
├── AgenciaViajes.iml / .idea/              # IntelliJ IDEA module
├── fotos/                                  # Profile photos (generated)
├── documentos/<cédula>/                    # Driver documents (generated)
└── src/
    ├── main/
    │   └── Main.java                       # Entry point: error handler, splash, login, simulation clock
    ├── DB/
    │   ├── ConexionOracle.java             # Connection pool + configuration
    │   ├── EsquemaOracle.java              # Schema creation and versioned migrations
    │   ├── Bd.java                         # JDBC helpers, transactions, sequential codes
    │   └── ProbarConexion.java             # Connection test tool
    ├── objetosNegocio/                     # Domain objects (Usuario, Viaje, Reserva, Liquidacion…)
    ├── persistencia/                       # DAOs per table + AlmacenTexto (Oracle-backed)
    ├── interfaces/IFachada.java            # Facade contract
    ├── fachadas/FachadaArchivos.java       # Facade implementation (singleton)
    ├── control/                            # Controllers, simulation, billing, reports, initial data
    ├── objetosServicio/                    # Permisos, Seguridad, InformePdf, Bancos, Fecha, Marca…
    ├── excepciones/                        # Validador, PersistenciaException, FachadaException
    ├── InterfacesUsuario/                  # Swing windows, panels and dialogs
    │   ├── PantallaInicioSesion / PantallaRegistro / PantallaRecuperarClave / PantallaConfiguracionInicial
    │   ├── PantallaPrincipal                # Administration window (tabs by job title)
    │   ├── PantallaPortalPasajero / PantallaPortalConductor
    │   ├── PanelGestion*                    # Empresas, Usuarios, Conductores, Pasajeros, Buses, Rutas, Viajes, Reservas, Facturas
    │   ├── PanelComprarPasaje / PanelBilletera / PanelMisPasajes / PanelMisPagos
    │   ├── PanelDespacho / PanelPagosConductores / PanelRecargasAdmin / PanelNovedadesAdmin
    │   ├── PanelMapaRuta / PanelMapaVivo / DialogoMapaRuta / DialogoSeguimientoViaje / DialogoMapaAsientos
    │   ├── Dialogo*                         # Forms, searches, invoices, reports, profile…
    │   └── Tema / IconoApp / SplashScreen / SelectorFecha / ValidacionUI / ExportadorTabla / Navegador
    └── mantenimiento/
        └── RestablecerSistema.java         # Maintenance tool: back to a fresh install
```

---

## 🏗 Architecture and Design Notes

- **Layers:** `InterfacesUsuario` (Swing) → `control` (business rules, returns `ResultadoOperacion`) → `fachadas` / `persistencia` (DAOs) → `DB` (JDBC + Oracle).
- **Facade:** `FachadaArchivos` (singleton) gives controllers a single entry point to the DAOs and resolves the relations between objects.
- **Uniform results:** controllers return `ResultadoOperacion` (success flag, message, optional data), so the GUI never needs to catch persistence errors.
- **Undo on failure:** multi-step operations (buying a ticket, cancelling a trip) undo what they already did when a later step fails — no half-charged wallets or orphan seats.
- **Idempotent start-up:** the initial data is loaded once and marked in `CONFIGURACION`; the reset tool removes the marks.
- **Permissions in one place:** `Permisos` is the only class that decides which module a job title sees or edits; the main window builds its tabs from it.
- **Time-driven state:** `ControladorSimulacion.avanzar()` derives the state and position of every trip from the clock, so trips move without a server.
- **No UI assets or document libraries:** icons are drawn with Graphics2D; `.xlsx` and `.pdf` are written by the project.
- **Schema as code:** `EsquemaOracle` keeps the schema versioned inside the application; a fresh database needs no manual script.

---

## ⚠️ Known Limitations

This is an **educational project**, not a production ticketing platform.

- **Single-machine simulation:** payments are simulated (no real card gateway, no bank); the “bus” position is computed from the clock, not from GPS.
- **Tickets are final by design:** passengers cannot cancel or get refunds; only a trip cancelled by the agency refunds the wallets.
- **Local personal data:** `documentos/` and `fotos/` can contain real IDs and photos. They are generated at run time — add them to `.gitignore` before committing (`fotos/`, `documentos/`).
- **Sample accounts share one initial password** (temporary, forced change at first login). Set `RUTAVIVA_CLAVE_DEMO` before the first run if you want something else.
- **Sample data is for demonstration** (names, cédulas and phones are fictitious; cédulas are generated with a valid check digit).
- **Legacy file names:** some classes still receive names such as `"empresas.dat"`; they are ignored and only kept for compatibility.
- **Some classes come from decompiled sources:** a number of older classes (for example several `PanelGestion*` and `DialogoFormulario*` classes, `Validador` and `ControladorReservas`) still carry the “recreated from a .class file” header and a looser style than the rest.
- **Spanish only:** the interface and messages are in Spanish.

---

## 🛠 Troubleshooting

| Problem | Solution |
|---------|----------|
| `Falta 'url' en oracle.properties (o la variable de entorno ORACLE_URL)` | Create `oracle.properties` in the folder you run the program from, or define `ORACLE_URL`, `ORACLE_USER` and `ORACLE_PASSWORD`. |
| `ORA-01017: invalid username/password` | Check `user` and `password` in `oracle.properties`. |
| `ORA-12514` / `ORA-12541` (listener / service not found) | Start the Oracle service and check host, port and service name (`XEPDB1`) in the URL. |
| `ORA-01950: no privileges on tablespace` | Give the user quota: `ALTER USER AGENCIA_VIAJES QUOTA UNLIMITED ON USERS;`. |
| `No suitable driver found for jdbc:oracle` | Put `lib/ojdbc11-23.5.0.24.07.jar` on the classpath (the IDE does it from the project libraries). |
| `No se pudo preparar la base de datos Oracle` at start-up | The schema user lacks `CREATE TABLE`; grant `CONNECT, RESOURCE` or fix the connection. Test it with `DB.ProbarConexion`. |
| `error: invalid target release` | Use JDK 21 or newer, or lower `javac.source/target` in `nbproject/project.properties`. |
| Cannot log in as the main administrator | Use `admin` / `CambiarAdmin1` (or your `RUTAVIVA_ADMIN_*` values). It is created on the first start only. |
| Account locked | Wait 2 minutes after 5 failed logins (15 minutes for password recovery). |
| A driver cannot log in | The account is probably `PENDIENTE`: an administrator must review and approve it in *Usuarios*. |
| A trip does not appear for sale | It may be *Por aceptar* (driver has not accepted), sales closed (30 min before departure), departed, or out of seats. |
| “Saldo insuficiente” when buying | Top up the wallet first (*Mi billetera → Solicitar recarga*). Cash top-ups wait for finance approval. |
| Initial data did not load | Look at the start-up warnings; a partial load retries on the next start. For a clean start, use the reset tool. |
| Reset everything | Close the app and run `mantenimiento.RestablecerSistema`, then log in as `admin` / `CambiarAdmin1`. |

---

## 👤 Author, Repository and License

- **Author:** Christopher Josué Villón Peralta
- **Repository:** <https://github.com/ChristopherVP/AgenciaDeViajes>
- **Context:** university project.
- **License:** *not specified yet* — add a `LICENSE` file (for example MIT) if you want others to reuse the code.
