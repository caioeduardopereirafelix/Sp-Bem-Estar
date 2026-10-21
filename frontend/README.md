# Front-end do SP Bem-Estar

React 18 + TypeScript + Vite, Tailwind CSS 4 e componentes no padrão shadcn/ui (Radix) em `src/components/ui`.
O mapa usa Leaflet direto, sem wrapper.

```bash
cd frontend
npm install
npm run dev     # http://localhost:5173, com proxy de /api para o Spring em :8080
npm run build   # gera o site em ../src/main/resources/static
```

Depois do `npm run build`, o `./mvnw spring-boot:run` já serve o front em http://localhost:8080.
