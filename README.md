# Sync Batch Data - Sincronização Bidirecional

Projeto Spring Batch para sincronização de dados entre múltiplas fontes externas e base local.

## Tecnologias
- Java 21
- Spring Boot 3.4+
- Spring Batch 5
- RabbitMQ (principal)
- Dynamic DataSource Routing
- Flyway

## Como rodar

```bash
docker-compose up -d
./mvnw spring-boot:run
```

Acesse:
- App: http://localhost:8080
- RabbitMQ: http://localhost:15672
- Grafana: http://localhost:3001