// Наполняет тестовую кампанию примерами всех рас, классов и типов.
// Запуск: node scripts/seed-test-icons.js (после обновления тестового сервера).
const BASE = process.env.DND_TEST_URL || 'http://81.177.139.30:8091';
const LOGIN = 'icons';
const PASSWORD = 'icons2026';
const EMAIL = 'icons@example.com';
const TITLE = 'Иконки: расы, классы, типы';

async function call(path, opts = {}, token) {
  const headers = { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) };
  const res = await fetch(BASE + path, { ...opts, headers });
  const text = await res.text();
  let body; try { body = JSON.parse(text); } catch { body = text; }
  return { status: res.status, body };
}

const post = (path, body, token) => call(path, { method: 'POST', body: JSON.stringify(body) }, token);
const get = (path, token) => call(path, {}, token);

function itemsOf(value) {
  if (Array.isArray(value)) return value;
  return value && Array.isArray(value.items) ? value.items : [];
}

(async () => {
  // 1. Вход или регистрация
  let auth = await post('/api/auth/login', { login: LOGIN, password: PASSWORD, platform: 'android' });
  if (auth.status !== 200) {
    auth = await post('/api/auth/register', {
      login: LOGIN, email: EMAIL, password: PASSWORD, displayName: 'Тест иконок', platform: 'android',
    });
  }
  if (auth.status !== 200 && auth.status !== 201) {
    console.error('auth failed', auth.status, auth.body);
    process.exit(1);
  }
  const token = auth.body.accessToken;
  console.log('auth ok, user', auth.body.user.login);

  // 2. Кампания
  const campaigns = itemsOf((await get('/api/campaigns', token)).body);
  let campaign = campaigns.find((c) => c.title === TITLE);
  if (!campaign) {
    const created = await post('/api/campaigns', { title: TITLE, description: 'Проверка значков рас, классов, локаций и предметов' }, token);
    if (created.status !== 201) { console.error('campaign', created.status, created.body); process.exit(1); }
    campaign = created.body;
    console.log('campaign created', campaign.id);
  } else {
    console.log('campaign reused', campaign.id);
  }
  const cid = campaign.id;

  // 3. Справочники
  const races = (await get('/api/races', token)).body.map((r) => r.name);
  const classes = (await get('/api/classes', token)).body.map((r) => r.name);
  const locationTypes = (await get('/api/location-types', token)).body.map((r) => r.name);
  const itemTypes = (await get('/api/item-types', token)).body.map((r) => r.name);
  console.log('refs', races.length, classes.length, locationTypes.length, itemTypes.length);

  const existing = async (kind) => new Set(itemsOf((await get(`/api/campaigns/${cid}/${kind}?limit=500`, token)).body).map((x) => x.name));

  // 4. Персонажи: раса × пол
  const genders = [
    ['female', 'Ж'],
    ['male', 'М'],
    ['unknown', 'Не определён'],
  ];
  const haveCharacters = await existing('characters');
  let charCount = 0;
  for (const race of races) {
    for (const [code, label] of genders) {
      const name = `${race} ${label}`;
      if (haveCharacters.has(name)) continue;
      const res = await post(`/api/campaigns/${cid}/characters`, {
        name, race, klass: 'Неизвестно', gender: code,
      }, token);
      if (res.status !== 201) { console.error('character', name, res.status, res.body); continue; }
      charCount++;
    }
  }
  console.log('characters +', charCount);

  // 5. Герои: имя = класс, раса по кругу, пол по кругу
  const heroRaces = races.filter((r) => r !== 'Неизвестно' && r !== 'Без расы');
  const haveHeroes = await existing('heroes');
  let heroCount = 0, i = 0;
  for (const klass of classes) {
    if (haveHeroes.has(klass)) continue;
    const race = heroRaces[i % heroRaces.length];
    const gender = genders[i % genders.length][0];
    const res = await post(`/api/campaigns/${cid}/heroes`, {
      name: klass, race, klass, gender,
    }, token);
    if (res.status !== 201) { console.error('hero', klass, res.status, res.body); continue; }
    i++; heroCount++;
  }
  console.log('heroes +', heroCount);

  // 6. Предметы: имя = тип
  const haveItems = await existing('items');
  let itemCount = 0;
  for (const type of itemTypes) {
    if (haveItems.has(type)) continue;
    const res = await post(`/api/campaigns/${cid}/items`, { name: type, itemType: type }, token);
    if (res.status !== 201) { console.error('item', type, res.status, res.body); continue; }
    itemCount++;
  }
  console.log('items +', itemCount);

  // 7. Локации: имя = тип
  const haveLocations = await existing('locations');
  let locCount = 0;
  for (const type of locationTypes) {
    if (haveLocations.has(type)) continue;
    const res = await post(`/api/campaigns/${cid}/locations`, { name: type, locationType: type }, token);
    if (res.status !== 201) { console.error('location', type, res.status, res.body); continue; }
    locCount++;
  }
  console.log('locations +', locCount);

  // 8. Приглашение, чтобы зайти своим аккаунтом
  const invite = await post(`/api/campaigns/${cid}/invites`, { role: 'EDITOR_MASTER', expiresInHours: 168 }, token);
  if (invite.status === 201) {
    console.log('INVITE_CODE=' + invite.body.token);
    console.log('INVITE_URL=' + invite.body.url);
  } else {
    console.log('invite', invite.status, invite.body);
  }
  console.log('CAMPAIGN_ID=' + cid);
  console.log('LOGIN=' + LOGIN);
  console.log('PASSWORD=' + PASSWORD);
  console.log('WEB=' + BASE + '/');
})();
