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

Java 25 · Spring Boot 4 (Spring MVC con virtual threads, RestClient) · Actuator · Maven · JUnit 5 + AssertJ + WireMock.

## Configuración

En [application.yml](src/main/resources/application.yml). Cada propiedad se puede sobrescribir con su variable de entorno, por ejemplo `PRODUCT_API_BASE_URL`.

| Propiedad                             | Valor por defecto       | Descripción                                          |
| ------------------------------------- | ----------------------- | ---------------------------------------------------- |
| `product-api.base-url`                | `http://localhost:3001` | URL de los APIs de producto existentes               |
| `product-api.connect-timeout`         | `5s`                    | Tiempo máximo para establecer la conexión            |
| `product-api.read-timeout`            | `10s`                   | Tiempo máximo de espera de la respuesta              |
| `product-api.max-concurrent-requests` | `300`                   | Llamadas simultáneas máximas a los APIs de producto  |
| `product-api.max-wait-for-slot`       | `1s`                    | Espera máxima por una plaza libre antes de desistir  |

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
- **Timeouts explícitos en las llamadas a los APIs de producto.** Por defecto el cliente HTTP espera indefinidamente. El de lectura (10 s) se fijó midiendo con el test de rendimiento: deja llegar las respuestas lentas pero razonables y solo descarta las claramente anómalas. Como los similares se piden en paralelo, es también lo máximo que puede tardar una respuesta cuando uno de ellos se queda colgado. Un valor más bajo daría respuestas más rápidas pero con más similares descartados; cuánto merece la pena esperar es una decisión de producto, por eso es configurable.
- **El cliente traduce las respuestas HTTP al dominio y no decide nada más:** un 404 es "producto no encontrado"; un 5xx, un timeout o un fallo de conexión es "servicio no disponible". Qué hacer en cada caso lo decide el caso de uso.
- **Los detalles de los similares se piden en paralelo**, cada uno en un virtual thread, y la respuesta mantiene el orden de los ids. Así el tiempo de respuesta es el del similar más lento, no la suma de todos.
- **Límite de llamadas simultáneas a los APIs de producto**, con un `Semaphore`. Protege al servicio del que dependemos frente a picos de tráfico y evita que las llamadas lentas se acumulen sin control. Si no hay plaza libre en el tiempo máximo de espera, la llamada se trata como "servicio no disponible". El valor (300) se fijó midiendo con el test de rendimiento.
- **Los productos similares que fallan se descartan.** Si el detalle de un similar no existe o no se puede obtener, se omite de la respuesta (queda registrado en el log) y se devuelven los demás en su orden. Es preferible mostrar parte de los similares a no mostrar ninguno.
- **Códigos de respuesta.** 404 si el producto no existe, como indica el contrato. Si falla la consulta de los ids similares, 502 (Bad Gateway): el contrato no contempla este caso y es el código estándar cuando falla un servicio del que se depende.
- **Controller escrito a mano.** Con un único endpoint es lo más claro; con más endpoints, generaría la interfaz y los modelos a partir del contrato OpenAPI (API-first) para que código y contrato no se desincronicen.
