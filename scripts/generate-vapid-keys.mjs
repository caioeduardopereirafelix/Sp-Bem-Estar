// Gera o par de chaves VAPID (P-256) das notificações push, sem dependências: node scripts/generate-vapid-keys.mjs
// Cole a saída no .env. Trocar o par invalida as inscrições já feitas: os navegadores precisam se inscrever de novo.
import {generateKeyPairSync} from 'node:crypto';

const {publicKey, privateKey} = generateKeyPairSync('ec', {namedCurve: 'prime256v1'});
const pub = publicKey.export({format: 'jwk'});
const priv = privateKey.export({format: 'jwk'});
// chave pública no formato que o navegador espera: ponto não comprimido (0x04 || x || y) em base64url
const uncompressed = Buffer.concat([Buffer.from([4]), Buffer.from(pub.x, 'base64url'), Buffer.from(pub.y, 'base64url')]);

console.log(`PUSH_VAPID_PUBLIC_KEY=${uncompressed.toString('base64url')}`);
console.log(`PUSH_VAPID_PRIVATE_KEY=${priv.d}`);
console.log('PUSH_VAPID_SUBJECT=mailto:contato@seu-dominio.gov.br');
