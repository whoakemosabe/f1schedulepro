import webpush from 'web-push';

const keys = webpush.generateVAPIDKeys();

console.log('\nVAPID keys generated. Add these as Replit Secrets:\n');
console.log('VAPID_PUBLIC_KEY =', keys.publicKey);
console.log('VAPID_PRIVATE_KEY =', keys.privateKey);
console.log('\nAlso add:');
console.log('VAPID_CONTACT = mailto:your@email.com');
console.log('VITE_PUSH_SERVER_URL = <your push server URL>\n');
