/*
 * 生コン打設管理 — サービスワーカー
 *
 * 電波の届かない現場でも開けるように、アプリ一式を端末に持たせておく。
 * 画面（HTML）は「つながれば最新を取り、つながらなければ手元の控えを使う」。
 * 画像などは手元にあればそれを即返す。
 *
 * index.html を更新したら CACHE の番号を上げること。
 */
const CACHE  = 'dansetsu-v1';
const ASSETS = [
  './',
  './index.html',
  './manifest.webmanifest',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-180.png',
  './icons/icon-maskable-512.png',
  './icons/icon.svg',
];

self.addEventListener('install', e=>{
  e.waitUntil(
    caches.open(CACHE)
      .then(c => c.addAll(ASSETS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', e=>{
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k!==CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e=>{
  const req = e.request;
  if(req.method !== 'GET') return;
  const url = new URL(req.url);
  if(url.origin !== self.location.origin) return;

  // 画面の読み込み：オンラインなら最新、だめなら控え
  if(req.mode === 'navigate'){
    e.respondWith(
      fetch(req)
        .then(res=>{
          const copy = res.clone();
          caches.open(CACHE).then(c => c.put('./index.html', copy));
          return res;
        })
        .catch(()=> caches.match('./index.html').then(r => r || caches.match('./')))
    );
    return;
  }

  // それ以外：手元にあれば即返し、裏で控えを更新
  e.respondWith(
    caches.match(req).then(hit=>{
      const net = fetch(req).then(res=>{
        if(res && res.status===200 && res.type==='basic'){
          const copy = res.clone();
          caches.open(CACHE).then(c => c.put(req, copy));
        }
        return res;
      }).catch(()=> hit);
      return hit || net;
    })
  );
});
