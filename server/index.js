import express from 'express';
import webpush from 'web-push';
import { readFileSync, writeFileSync, existsSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import cron from 'node-cron';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SUBS_FILE = join(__dirname, 'subscriptions.json');
const NOTIFIED_FILE = join(__dirname, 'notified.json');

const { VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_CONTACT, PORT } = process.env;

if (!VAPID_PUBLIC_KEY || !VAPID_PRIVATE_KEY) {
  console.error('Missing VAPID_PUBLIC_KEY or VAPID_PRIVATE_KEY.');
  console.error('Run: node server/generate-vapid.js');
  process.exit(1);
}

webpush.setVapidDetails(
  VAPID_CONTACT || 'mailto:admin@example.com',
  VAPID_PUBLIC_KEY,
  VAPID_PRIVATE_KEY
);

function loadJson(file, fallback) {
  if (!existsSync(file)) return fallback;
  try { return JSON.parse(readFileSync(file, 'utf8')); } catch { return fallback; }
}
function saveJson(file, data) {
  writeFileSync(file, JSON.stringify(data, null, 2));
}

const schedule = loadJson(join(__dirname, 'schedule.json'), []);

const app = express();
app.use(express.json());
app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Headers', 'Content-Type');
  res.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

app.get('/vapid-public-key', (_req, res) => {
  res.json({ publicKey: VAPID_PUBLIC_KEY });
});

app.post('/subscribe', (req, res) => {
  const { subscription, minutesBefore } = req.body;
  if (!subscription?.endpoint) return res.status(400).json({ error: 'Invalid subscription' });

  const subs = loadJson(SUBS_FILE, []);
  const idx = subs.findIndex(s => s.subscription.endpoint === subscription.endpoint);
  const entry = { subscription, minutesBefore: minutesBefore || 30 };

  if (idx >= 0) subs[idx] = entry;
  else subs.push(entry);
  saveJson(SUBS_FILE, subs);

  res.json({ ok: true });
});

app.post('/unsubscribe', (req, res) => {
  const { endpoint } = req.body;
  const subs = loadJson(SUBS_FILE, []).filter(s => s.subscription.endpoint !== endpoint);
  saveJson(SUBS_FILE, subs);
  res.json({ ok: true });
});

app.post('/update-settings', (req, res) => {
  const { endpoint, minutesBefore } = req.body;
  const subs = loadJson(SUBS_FILE, []);
  const sub = subs.find(s => s.subscription.endpoint === endpoint);
  if (sub) {
    sub.minutesBefore = minutesBefore;
    saveJson(SUBS_FILE, subs);
  }
  res.json({ ok: true });
});

async function sendPushNotifications() {
  const subs = loadJson(SUBS_FILE, []);
  if (!subs.length) return;

  const now = Date.now();
  const notifiedArr = loadJson(NOTIFIED_FILE, []);
  const notified = new Set(notifiedArr);
  let changed = false;

  for (const { subscription, minutesBefore } of subs) {
    const windowMs = minutesBefore * 60 * 1000;
    const subKey = subscription.endpoint.slice(-16);

    for (const race of schedule) {
      for (const session of race.sessions) {
        const sessionTime = new Date(session.dateUtc).getTime();
        const diff = sessionTime - now;
        const key = `${subKey}-${race.round}-${session.name}`;

        if (diff > 0 && diff <= windowMs && !notified.has(key)) {
          notified.add(key);
          changed = true;
          const minsLeft = Math.round(diff / 60000);
          const payload = JSON.stringify({
            title: 'F1 Schedule Pro',
            body: `${race.name} \u2014 ${session.name} starts in ${minsLeft} minute${minsLeft !== 1 ? 's' : ''}`,
            tag: `${race.round}-${session.name}`,
          });

          try {
            await webpush.sendNotification(subscription, payload);
            console.log(`Sent: ${race.name} ${session.name} (${minsLeft}m)`);
          } catch (err) {
            if (err.statusCode === 410 || err.statusCode === 404) {
              const remaining = loadJson(SUBS_FILE, [])
                .filter(s => s.subscription.endpoint !== subscription.endpoint);
              saveJson(SUBS_FILE, remaining);
            }
          }
        }
      }
    }
  }

  if (changed) {
    saveJson(NOTIFIED_FILE, [...notified].slice(-1000));
  }
}

cron.schedule('* * * * *', () => { void sendPushNotifications(); });
void sendPushNotifications();

const port = parseInt(PORT || '3001', 10);
app.listen(port, () => console.log(`F1 Push Server on port ${port}`));
