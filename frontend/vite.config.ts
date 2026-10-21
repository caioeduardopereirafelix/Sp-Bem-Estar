import path from 'node:path';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import {defineConfig} from 'vite';

// O build vai direto para os recursos estáticos do Spring Boot: `./mvnw spring-boot:run` já serve o front.
export default defineConfig({
    plugins: [react(), tailwindcss()],
    resolve: {alias: {'@': path.resolve(__dirname, 'src')}},
    server: {proxy: {'/api': 'http://localhost:8080'}},
    build: {
        outDir: process.env.OUT_DIR ?? path.resolve(__dirname, '../src/main/resources/static'),
        emptyOutDir: true,
    },
});
