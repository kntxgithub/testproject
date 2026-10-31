/**
 * かんたん予定追加 — Google カレンダー用 ボタン式 予定登録アプリ
 *
 * Google Apps Script の Web アプリとして動作します。
 * カテゴリ（破壊試験 / 営業 / 修理 …）ごとに登録先カレンダーを設定でき、
 * 登録時にカレンダーを個別に変更することもできます。
 */

/**
 * カテゴリ定義。ここを編集するとタブが増減します。
 *  type: 'concrete'    … 打設日から材齢◯日の破壊試験日を自動計算するフォーム
 *        'appointment' … 日付・時刻・所要時間をボタンで選ぶ一般的な予定フォーム
 *  prefix: 予定タイトルの先頭に付く文字
 */
const CATEGORIES = [
  { key: 'concrete', label: '破壊試験', type: 'concrete',    prefix: '' },
  { key: 'sales',    label: '営業',     type: 'appointment', prefix: '【営業】' },
  { key: 'repair',   label: '修理',     type: 'appointment', prefix: '【修理】' },
];

/** 破壊試験フォームでボタン表示する材齢（日） */
const CONCRETE_AGES = [7, 28];

/** 日本の祝日カレンダー（土日祝の警告表示に使用） */
const HOLIDAY_CALENDAR_ID = 'ja.japanese#holiday@group.v.calendar.google.com';

const RECENT_SITES_KEY = 'recentSites';
const SETTINGS_KEY = 'calendarSettings';
const MAX_RECENT_SITES = 30;

function doGet() {
  return HtmlService.createHtmlOutputFromFile('index')
    .setTitle('かんたん予定追加')
    .addMetaTag('viewport', 'width=device-width, initial-scale=1, maximum-scale=1')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
}

/** 画面の初期表示に必要なデータをまとめて返す */
function getInitData() {
  const calendars = CalendarApp.getAllCalendars()
    .filter(function (c) { return c.getId() !== HOLIDAY_CALENDAR_ID; })
    .map(function (c) {
      return { id: c.getId(), name: c.getName(), color: c.getColor(), owned: c.isOwnedByMe() };
    })
    .sort(function (a, b) { return (b.owned - a.owned) || a.name.localeCompare(b.name, 'ja'); });

  const defaultId = CalendarApp.getDefaultCalendar().getId();
  const saved = getSettings_();
  const settings = {};
  CATEGORIES.forEach(function (cat) {
    const id = saved[cat.key];
    settings[cat.key] = calendars.some(function (c) { return c.id === id; }) ? id : defaultId;
  });

  return {
    categories: CATEGORIES,
    concreteAges: CONCRETE_AGES,
    calendars: calendars,
    settings: settings,
    recentSites: getRecentSites_(),
  };
}

/** カテゴリ → カレンダー の対応を保存 */
function saveSettings(settings) {
  const clean = {};
  CATEGORIES.forEach(function (cat) {
    if (settings && typeof settings[cat.key] === 'string') clean[cat.key] = settings[cat.key];
  });
  PropertiesService.getUserProperties().setProperty(SETTINGS_KEY, JSON.stringify(clean));
  return clean;
}

/**
 * 指定日付リストのうち祝日のものを返す。
 * @param {string[]} isoDates 'YYYY-MM-DD' の配列
 * @return {Object<string,string>} { 'YYYY-MM-DD': '祝日名' }
 */
function getHolidays(isoDates) {
  const result = {};
  if (!isoDates || !isoDates.length) return result;
  let cal;
  try {
    cal = CalendarApp.getCalendarById(HOLIDAY_CALENDAR_ID);
  } catch (e) {
    return result;
  }
  if (!cal) return result;

  const sorted = isoDates.slice().sort();
  const start = parseDate_(sorted[0]);
  const end = parseDate_(sorted[sorted.length - 1]);
  end.setDate(end.getDate() + 1);
  const tz = Session.getScriptTimeZone();
  cal.getEvents(start, end).forEach(function (ev) {
    const key = Utilities.formatDate(ev.getAllDayStartDate(), tz, 'yyyy-MM-dd');
    if (isoDates.indexOf(key) !== -1) result[key] = ev.getTitle();
  });
  return result;
}

/**
 * 予定をまとめて登録する。
 * @param {{calendarId:string, siteName?:string, events:Array<{
 *   title:string, date:string, allDay:boolean, startTime?:string, durationMin?:number,
 *   location?:string, description?:string, reminderMin?:number}>}} payload
 * @return {{calendarName:string, created:Array<{id:string,title:string,date:string}>}}
 */
function addEvents(payload) {
  if (!payload || !payload.events || !payload.events.length) {
    throw new Error('登録する予定がありません');
  }
  const cal = CalendarApp.getCalendarById(payload.calendarId);
  if (!cal) throw new Error('カレンダーが見つかりません: ' + payload.calendarId);

  const created = [];
  try {
    payload.events.forEach(function (e) {
      if (!e.title) throw new Error('タイトルが空です');
      const options = {};
      if (e.location) options.location = e.location;
      if (e.description) options.description = e.description;

      let ev;
      if (e.allDay) {
        ev = cal.createAllDayEvent(e.title, parseDate_(e.date), options);
      } else {
        const start = parseDateTime_(e.date, e.startTime);
        const end = new Date(start.getTime() + (Number(e.durationMin) || 60) * 60 * 1000);
        ev = cal.createEvent(e.title, start, end, options);
      }
      if (typeof e.reminderMin === 'number' && e.reminderMin >= 0) {
        ev.removeAllReminders();
        ev.addPopupReminder(e.reminderMin);
      }
      created.push({ id: ev.getId(), title: e.title, date: e.date });
    });
  } catch (err) {
    // 途中で失敗した場合は、作成済みの分を取り消して二重登録を防ぐ
    created.forEach(function (c) {
      try { cal.getEventById(c.id).deleteEvent(); } catch (ignore) {}
    });
    throw err;
  }

  if (payload.siteName) rememberSite_(payload.siteName);
  return { calendarName: cal.getName(), created: created };
}

/** 直前に登録した予定を取り消す */
function deleteEvents(calendarId, eventIds) {
  const cal = CalendarApp.getCalendarById(calendarId);
  if (!cal) throw new Error('カレンダーが見つかりません');
  let count = 0;
  (eventIds || []).forEach(function (id) {
    const ev = cal.getEventById(id);
    if (ev) { ev.deleteEvent(); count++; }
  });
  return count;
}

// ---- 内部ヘルパー ---------------------------------------------------------

function getSettings_() {
  const raw = PropertiesService.getUserProperties().getProperty(SETTINGS_KEY);
  try { return raw ? JSON.parse(raw) : {}; } catch (e) { return {}; }
}

function getRecentSites_() {
  const raw = PropertiesService.getUserProperties().getProperty(RECENT_SITES_KEY);
  try { return raw ? JSON.parse(raw) : []; } catch (e) { return []; }
}

function rememberSite_(name) {
  name = String(name).trim();
  if (!name) return;
  const list = getRecentSites_().filter(function (s) { return s !== name; });
  list.unshift(name);
  PropertiesService.getUserProperties()
    .setProperty(RECENT_SITES_KEY, JSON.stringify(list.slice(0, MAX_RECENT_SITES)));
}

/** 'YYYY-MM-DD' → スクリプトのタイムゾーンでの 0:00 の Date */
function parseDate_(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(iso));
  if (!m) throw new Error('日付の形式が不正です: ' + iso);
  return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
}

/** 'YYYY-MM-DD' + 'HH:MM' → Date */
function parseDateTime_(iso, hhmm) {
  const d = parseDate_(iso);
  const m = /^(\d{1,2}):(\d{2})$/.exec(String(hhmm || ''));
  if (!m) throw new Error('時刻の形式が不正です: ' + hhmm);
  d.setHours(Number(m[1]), Number(m[2]), 0, 0);
  return d;
}
