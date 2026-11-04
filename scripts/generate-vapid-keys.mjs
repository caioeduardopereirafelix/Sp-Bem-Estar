import {generateKeyPairSync} from 'node:crypto';

const {publicKey, privateKey} = generateKeyPairSync('ec', {namedCurve: 'prime256v1'});
const pub = publicKey.export({format: 'jwk'});
const priv = privateKey.export({format: 'jwk'});
const uncompressed = Buffer.concat([Buffer.from([4]), Buffer.from(pub.x, 'base64url'), Buffer.from(pub.y, 'base64url')]);

console.log(`PUSH_VAPID_PUBLIC_KEY=${uncompressed.toString('base64url')}`);
console.log(`PUSH_VAPID_PRIVATE_KEY=${priv.d}`);
console.log('PUSH_VAPID_SUBJECT=mailto:contato@seu-dominio.gov.br');
