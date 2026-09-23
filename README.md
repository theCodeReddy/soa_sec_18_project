# MarketGrid Commerce Platform

Spring Boot microservices baseline for a multi-tenant marketplace.

## Services

- `discovery-server`: Eureka registry on `8761`
- `gateway`: Spring Cloud Gateway on `8080`
- `user-service`: registration/login and JWT issuance on `8081`
- `vendor-service`: tenant-scoped vendor management on `8082`
- `product-service`: vendor product catalogs and stock reservation on `8083`
- `order-service`: combined multi-vendor orders on `8084`

## Run locally

Prerequisites: JDK 21+, MySQL 8+, and Node.js 18+ for the frontend. Maven Wrapper scripts are included, so a separate Maven installation is not required.

```bash
./mvnw clean package
./mvnw -pl discovery-server spring-boot:run
./mvnw -pl user-service spring-boot:run
./mvnw -pl vendor-service spring-boot:run
./mvnw -pl product-service spring-boot:run
./mvnw -pl order-service spring-boot:run
./mvnw -pl gateway spring-boot:run
```

On Windows, use `mvnw.cmd` instead of `./mvnw`. Start the local MySQL 8 service, then apply the schema before starting the backend:

```powershell
Get-Content .\database\schema.sql | mysql -h 127.0.0.1 -P 3306 -u root -proot
```

## Frontend

The React/Vite customer and vendor console lives in `frontend` and runs on `5173`.

```powershell
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 after starting the backend gateway. Set `VITE_API_URL` when the gateway is not running on `http://localhost:8080`.

## MySQL schema

The MySQL connection is configured for `localhost:3306`, database `soasect18`, user `root`, and password `root`.
Create the project schema from `database/schema.sql` in MySQL Workbench, or run:

```powershell
Get-Content .\database\schema.sql | mysql -h 127.0.0.1 -P 3306 -u root -proot
```

Refresh the Schemas panel after running it; the `soasect18` schema and its tables will appear.

## Multi-vendor flow

1. `POST /api/auth/register`, then `POST /api/auth/login` to obtain a JWT.
2. Use `Authorization: Bearer <token>` to create vendors and products.
3. `POST /api/orders` with items from different vendors. The order service calls product service through Eureka/Feign and reserves each item before returning `CONFIRMED`.
4. `GET /api/orders/{id}` tracks the order.

All services now use MySQL for runtime reads and writes. Users, vendors, products, orders, and order items survive service restarts through the tables defined in `database/schema.sql`.
