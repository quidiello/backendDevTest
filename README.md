# Similar products API (backend dev test)

Servicio Spring Boot que expone `GET /product/{productId}/similar`: devuelve el detalle de los productos similares a uno dado, ordenados por similitud. Para ello combina dos APIs ya existentes (`/product/{id}/similarids` y `/product/{id}`).

- Enunciado original: [docs/test-statement.md](docs/test-statement.md)
- Contrato acordado con front: [similarProducts.yaml](similarProducts.yaml)
- APIs existentes: [existingApis.yaml](existingApis.yaml)

> El historial de commits sigue el orden en que se fue construyendo, por hitos.

## Requisitos

- JDK 25.
- Docker, para los mocks y el test de rendimiento.

Maven no hace falta: el proyecto incluye el Maven Wrapper (`mvnw`).

## Uso

| Comando                  | Descripción                           |
| ------------------------ | ------------------------------------- |
| `./mvnw spring-boot:run` | Arranca el servicio en el puerto 5000 |
| `./mvnw test`            | Lanza los tests                       |
| `./mvnw package`         | Genera el jar en `target/`            |

En Windows, `mvnw.cmd` en lugar de `./mvnw`.

## Test de rendimiento

Con el servicio arrancado:

```bash
docker compose up -d simulado influxdb grafana
docker compose run --rm k6 run scripts/test.js
```

Los mocks responden en `http://localhost:3001` y los resultados se ven en [Grafana](http://localhost:3000/d/Le2Ku9NMk/k6-performance-test).

## Stack

Java 25 · Spring Boot 4 (Spring MVC con virtual threads) · Actuator · Maven.

## Estructura

```
src/main/java/com/nunegal/similarproducts/
  domain/                  modelo y puertos; sin dependencias de Spring
  application/             casos de uso
  infrastructure/rest/     adaptador de entrada: el API REST
  infrastructure/client/   adaptador de salida: cliente de los APIs existentes
```

Arquitectura hexagonal ligera: el dominio no conoce HTTP ni Spring, y cada API externa queda detrás de un adaptador que se puede probar o sustituir por separado.

## Decisiones

- **Spring MVC con virtual threads.** Las llamadas a los APIs de producto pueden tardar; con virtual threads, una petición que espera respuesta no bloquea un hilo del sistema, y el código sigue siendo secuencial y fácil de leer.
- **Errores como Problem Details (RFC 9457)**, el formato estándar que Spring Boot ya soporta.
