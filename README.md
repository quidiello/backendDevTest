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

Para comparar ejecuciones, conviene reiniciar el mock antes de cada una (`docker compose restart simulado`): arrastra las respuestas lentas pendientes de la ejecución anterior y los resultados salen peores.

### Evolución

Resultados del test tras cada paso, con el mock reiniciado antes de cada ejecución. Es una ejecución por paso: sirven para ver la tendencia, no como cifras exactas.

Cada celda tiene dos valores, `peticiones completadas · tiempo medio de respuesta`:

- **Peticiones completadas:** cada escenario son 200 usuarios durante 10 s; cada usuario hace una petición, espera la respuesta, descansa 0,5 s y repite. Cuanto más rápida es la respuesta, más peticiones se completan. Si la respuesta fuera instantánea, cada usuario completaría 20 (10 s / 0,5 s), así que el máximo teórico es 4000.
- **Tiempo medio de respuesta** de esas peticiones.

| Paso | normal | slow | verySlow |
| ---- | ------ | ---- | -------- |
| Llamadas secuenciales | 2863 · 207 ms | 400 · 6,3 s | 0 · — |
| Llamadas en paralelo | 2821 · 217 ms | 400 · 5,2 s | 0 · — |
| Límite de llamadas simultáneas (300) | 2870 · 207 ms | 452 · 4,8 s | 172 · 2,0 s |
| Timeout de lectura (10 s) | 2878 · 204 ms | 442 · 4,8 s | 327 · 7,1 s |
| Caché | 3799 · 42 ms | 1898 · 0,6 s | 200 · 10,1 s |
| Ajuste de timeout y límite (6 s, 600) | 3799 · 32 ms | 2000 · 0,5 s | 400 · 6,0 s |
| Recordar los fallos (30 s) | 3712 · 46 ms | 2000 · 0,5 s | 1600 · 0,8 s |

- Sin timeout, las peticiones de `verySlow` no terminan durante el test.
- Con el límite, parte de las respuestas de `verySlow` son rápidas porque descartan similares por falta de plaza. Con la caché casi todas las llamadas se resuelven en memoria, apenas faltan plazas y las respuestas de `verySlow` tardan lo que el timeout.
- El ajuste final se eligió probando varias combinaciones de timeout y límite: es la que no descarta similares válidos y responde antes.
- Al recordar los fallos, solo la primera ráfaga de `verySlow` espera el timeout; las siguientes descartan al momento el producto que no responde.

## Stack

Java 25 · Spring Boot 4 (Spring MVC con virtual threads, RestClient, Actuator) · Caffeine · Micrometer · Maven · JUnit 5 + AssertJ + Mockito + WireMock.

## Configuración

En [application.yml](src/main/resources/application.yml). Cada propiedad se puede sobrescribir con su variable de entorno, por ejemplo `PRODUCT_API_BASE_URL`.

| Propiedad                             | Valor por defecto       | Descripción                                          |
| ------------------------------------- | ----------------------- | ---------------------------------------------------- |
| `product-api.base-url`                | `http://localhost:3001` | URL de los APIs de producto existentes               |
| `product-api.connect-timeout`         | `5s`                    | Tiempo máximo para establecer la conexión            |
| `product-api.read-timeout`            | `6s`                    | Tiempo máximo de espera de la respuesta              |
| `product-api.max-concurrent-requests` | `600`                   | Llamadas simultáneas máximas a los APIs de producto  |
| `product-api.max-wait-for-slot`       | `1s`                    | Espera máxima por una plaza libre antes de desistir  |
| `product-api.failure-ttl`             | `30s`                   | Tiempo durante el que no se vuelve a pedir el detalle de un producto que ha fallado |
| `spring.cache.caffeine.spec`          | `maximumSize=100000,expireAfterWrite=5m` | Tamaño máximo y tiempo de vida de la caché |

## Estructura

```
src/main/java/com/nunegal/similarproducts/
  domain/                  modelo y puertos; sin dependencias de Spring
  application/             casos de uso
  infrastructure/rest/     adaptador de entrada: el API REST
  infrastructure/client/   adaptador de salida: cliente de los APIs existentes
```

## Tests

`./mvnw test` lanza todos. Cada nivel se prueba por separado:

| Test | Qué prueba |
| ---- | ---------- |
| `SimilarProductsServiceTest` | El caso de uso, con el repositorio simulado con Mockito: orden, llamadas en paralelo, similares descartados y la métrica |
| `ProductApiClientTest` | El cliente contra un API simulado con WireMock: respuestas, errores, timeouts y el límite de llamadas simultáneas |
| `ProductApiClientCacheTest` | Qué se guarda en la caché y qué no |
| `SimilarProductsControllerTest` | El endpoint: respuesta y códigos de error |
| `SimilarProductsIntegrationTest` | De extremo a extremo, con la aplicación completa y los APIs simulados con WireMock |

## Decisiones

### Diseño

- **Arquitectura hexagonal ligera.** El dominio no conoce HTTP ni Spring; el caso de uso trabaja contra un puerto (`ProductRepository`) y el cliente HTTP es solo una implementación de ese puerto, que se puede probar o sustituir por separado.
- **Spring MVC con virtual threads.** Las llamadas a los APIs de producto pueden tardar; con virtual threads, una petición que espera respuesta no bloquea un hilo del sistema, y el código sigue siendo secuencial y fácil de leer.
- **El cliente traduce las respuestas HTTP al dominio y no decide nada más:** un 404 es "producto no encontrado"; un 5xx, un timeout o un fallo de conexión es "servicio no disponible". Qué hacer en cada caso lo decide el caso de uso.
- **Códigos de respuesta.** 404 si el producto no existe, como indica el contrato. Si falla la consulta de los ids similares, 502 (Bad Gateway): el contrato no contempla este caso y es el código estándar cuando falla un servicio del que se depende.
- **Errores como Problem Details (RFC 9457)**, el formato estándar que Spring Boot ya soporta.
- **Controller escrito a mano.** Con un único endpoint es lo más claro.

### Rendimiento y resiliencia

- **Timeouts explícitos en las llamadas a los APIs de producto.** Por defecto el cliente HTTP espera indefinidamente. El de lectura (6 s) se fijó midiendo con el test de rendimiento: deja llegar las respuestas lentas pero razonables de los mocks (5 s) y descarta las anómalas; con un servicio real se fijaría a partir de sus tiempos de respuesta. Es también lo máximo que puede tardar una respuesta cuando un similar se queda colgado. Cuánto merece la pena esperar es una decisión de producto, por eso es configurable.
- **Los detalles de los similares se piden en paralelo**, cada uno en un virtual thread, y la respuesta mantiene el orden de los ids. Así el tiempo de respuesta es el del similar más lento, no la suma de todos.
- **Límite de llamadas simultáneas a los APIs de producto**, con un `Semaphore`. Protege al servicio del que dependemos frente a picos de tráfico y evita que las llamadas lentas se acumulen sin control. Si no hay plaza libre en el tiempo máximo de espera, la llamada se trata como "servicio no disponible". El valor (600) se fijó midiendo con el test de rendimiento: cubre el pico de llamadas del test sin descartar productos válidos. Con un servicio real se ajustaría a la capacidad de ese servicio.
- **Caché en memoria (Caffeine) de las respuestas de los APIs de producto**, tanto de los ids similares como del detalle. Los mismos productos se piden constantemente, así que la mayoría de peticiones dejan de llegar al API. Se recuerda también que un producto no existe; los errores y timeouts no se guardan, para volver a intentarlo en la siguiente petición. El tiempo de vida (5 min) limita cuánto puede tardar en verse un cambio de precio o disponibilidad.
- **Sin agrupar peticiones simultáneas del mismo producto en la caché.** Spring permite que, si varias peticiones piden a la vez un dato que no está en caché, solo una llame al API (`sync = true`). Se probó y se descartó: con un producto que siempre agota el timeout, las peticiones que esperaban lo reintentaban una detrás de otra y acumulaban esperas de 20–30 s.
- **Se recuerda durante un tiempo corto (30 s) que el detalle de un producto ha fallado** por timeout o error del API, y mientras tanto ese similar se descarta sin llamar al API. Así un producto que no responde solo hace esperar a las primeras peticiones, no a todas. Quedarse sin plaza no cuenta como fallo del producto. El precio es que un fallo puntual oculta el producto durante ese tiempo, por eso es corto y configurable.
- **Los productos similares que fallan se descartan.** Si el detalle de un similar no existe o no se puede obtener, se omite de la respuesta y se devuelven los demás en su orden. Es preferible mostrar parte de los similares a no mostrar ninguno.
- **Los similares descartados quedan en el log y en una métrica.** El log da el detalle de cada caso (qué producto y por qué); la métrica `similar.products.skipped`, con el motivo (`not_found` o `unavailable`) como etiqueta, permite vigilar cuántos se descartan. Se consulta en `/actuator/metrics/similar.products.skipped`.

## Mejoras posibles

- **Caché asíncrona que comparta el resultado entre peticiones simultáneas**, también cuando es un error, para que solo una llame al API sin que las demás reintenten una detrás de otra.
- **Caché compartida** (por ejemplo, Redis) si el servicio se despliega en varias instancias: ahora cada instancia tiene la suya.
- **Generar el controller y los modelos a partir del contrato OpenAPI (API-first)** cuando haya más endpoints, para que código y contrato no se desincronicen.
- **Enviar las métricas a un sistema como Prometheus**, para tener histórico y alertas.
