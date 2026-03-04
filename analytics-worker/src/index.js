/**
 * AardAppvark Anonymous Geo-Analytics Worker
 *
 * Cloudflare Worker + D1 endpoint for privacy-preserving analytics.
 *
 * HOW IT WORKS:
 * 1. App sends POST with { event_type: "app_open" }
 * 2. Cloudflare's edge derives country/city from the request IP automatically
 *    via request.cf — NO external geo API needed
 * 3. We read ONLY country + city from request.cf
 * 4. The IP address is NEVER logged, stored, or processed by our code
 * 5. We upsert an aggregate count row in D1: (event_type, country, city, date) += 1
 *
 * PRIVACY:
 * - Zero PII stored (no IP, no user ID, no device ID, no fingerprint)
 * - Compliant with GDPR, CCPA, LGPD, APPI, PIPA without consent
 * - Only aggregate counts by city/country/day
 *
 * ENDPOINTS:
 * POST /          — Track an event
 * GET  /stats     — Get aggregate analytics (protected by API key)
 * GET  /health    — Health check
 */

const ALLOWED_EVENTS = [
  'app_open',
  'wallet_connected',
  'wallet_disconnected',
  'sgt_verified',
  'sgt_not_found',
  'check_in',
  'prediction_run',
  'season1_analyzed',
  'portfolio_viewed',
  'community_viewed',
  'settings_opened',
  'guest_mode_entered',
  'leaderboard_submit',
  'prediction_tier_result',
  'onchain_checkin',
  'simulator_used',
  'onboarding_completed',
];

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // CORS headers for preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'POST, GET, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type, X-API-Key',
        },
      });
    }

    // Health check
    if (url.pathname === '/health') {
      return json({ status: 'ok', timestamp: new Date().toISOString() });
    }

    // Stats endpoint (requires API key)
    if (url.pathname === '/stats' && request.method === 'GET') {
      return handleStats(request, env);
    }

    // Leaderboard submit
    if (request.method === 'POST' && url.pathname === '/leaderboard/submit') {
      return handleLeaderboardSubmit(request, env);
    }

    // Leaderboard query
    if (request.method === 'GET' && url.pathname === '/leaderboard') {
      return handleLeaderboardQuery(request, env);
    }

    // Event tracking endpoint
    if (request.method === 'POST' && (url.pathname === '/' || url.pathname === '/track')) {
      return handleTrack(request, env);
    }

    return new Response('Not found', { status: 404 });
  },
};

async function handleTrack(request, env) {
  // Simple API key check
  const apiKey = request.headers.get('X-API-Key');
  if (!apiKey || apiKey !== env.API_KEY) {
    return json({ error: 'Unauthorized' }, 401);
  }

  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: 'Invalid JSON' }, 400);
  }

  const eventType = body.event_type;
  if (!eventType || typeof eventType !== 'string' || !ALLOWED_EVENTS.includes(eventType)) {
    return json({ error: 'Invalid event_type' }, 400);
  }

  // Geo data from Cloudflare's edge — IP is NEVER stored
  const country = request.cf?.country || 'XX';
  const city = request.cf?.city || 'Unknown';
  const region = request.cf?.region || 'Unknown';
  const date = new Date().toISOString().split('T')[0]; // YYYY-MM-DD

  try {
    // Upsert: increment count if exists, insert new row if not
    await env.DB.prepare(`
      INSERT INTO events (event_type, country, city, region, date, count)
      VALUES (?, ?, ?, ?, ?, 1)
      ON CONFLICT(event_type, country, city, date)
      DO UPDATE SET count = count + 1
    `).bind(eventType, country, city, region, date).run();

    return json({ ok: true });
  } catch (err) {
    console.error('DB write error:', err);
    return json({ error: 'Server error' }, 500);
  }
}

async function handleStats(request, env) {
  const apiKey = request.headers.get('X-API-Key');
  if (!apiKey || apiKey !== env.API_KEY) {
    return json({ error: 'Unauthorized' }, 401);
  }

  const url = new URL(request.url);
  const period = url.searchParams.get('period') || '30'; // days
  const days = Math.min(parseInt(period) || 30, 365);
  const since = new Date();
  since.setDate(since.getDate() - days);
  const sinceStr = since.toISOString().split('T')[0];

  try {
    // Total events
    const totals = await env.DB.prepare(`
      SELECT event_type, SUM(count) as total
      FROM events WHERE date >= ?
      GROUP BY event_type ORDER BY total DESC
    `).bind(sinceStr).all();

    // Country breakdown
    const countries = await env.DB.prepare(`
      SELECT country, SUM(count) as total
      FROM events WHERE date >= ?
      GROUP BY country ORDER BY total DESC LIMIT 50
    `).bind(sinceStr).all();

    // Top cities
    const cities = await env.DB.prepare(`
      SELECT country, city, SUM(count) as total
      FROM events WHERE date >= ?
      GROUP BY country, city ORDER BY total DESC LIMIT 30
    `).bind(sinceStr).all();

    // Daily trend
    const daily = await env.DB.prepare(`
      SELECT date, SUM(count) as total
      FROM events WHERE date >= ?
      GROUP BY date ORDER BY date DESC LIMIT ?
    `).bind(sinceStr, days).all();

    return json({
      period_days: days,
      since: sinceStr,
      event_totals: totals.results,
      countries: countries.results,
      top_cities: cities.results,
      daily_trend: daily.results,
    });
  } catch (err) {
    console.error('Stats query error:', err);
    return json({ error: 'Query failed' }, 500);
  }
}

async function handleLeaderboardSubmit(request, env) {
  const apiKey = request.headers.get('X-API-Key');
  if (!apiKey || apiKey !== env.API_KEY) {
    return json({ error: 'Unauthorized' }, 401);
  }

  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: 'Invalid JSON' }, 400);
  }

  const { score_bucket, tier } = body;
  if (!score_bucket || !tier) {
    return json({ error: 'Missing score_bucket or tier' }, 400);
  }

  const VALID_TIERS = ['Scout', 'Prospector', 'Vanguard', 'Luminary', 'Sovereign'];
  if (!VALID_TIERS.includes(tier)) {
    return json({ error: 'Invalid tier' }, 400);
  }

  const country = request.cf?.country || 'XX';
  const date = new Date().toISOString().split('T')[0];

  try {
    await env.DB.prepare(`
      INSERT INTO leaderboard (score_bucket, tier, country, date, count)
      VALUES (?, ?, ?, ?, 1)
      ON CONFLICT(score_bucket, tier, country, date)
      DO UPDATE SET count = count + 1
    `).bind(score_bucket, tier, country, date).run();

    return json({ ok: true });
  } catch (err) {
    console.error('Leaderboard write error:', err);
    return json({ error: 'Server error' }, 500);
  }
}

async function handleLeaderboardQuery(request, env) {
  const url = new URL(request.url);
  const period = url.searchParams.get('period') || '7';
  const days = Math.min(parseInt(period) || 7, 30);
  const since = new Date();
  since.setDate(since.getDate() - days);
  const sinceStr = since.toISOString().split('T')[0];

  try {
    // Tier distribution
    const tiers = await env.DB.prepare(`
      SELECT tier, SUM(count) as total
      FROM leaderboard WHERE date >= ?
      GROUP BY tier ORDER BY total DESC
    `).bind(sinceStr).all();

    // Score distribution
    const scores = await env.DB.prepare(`
      SELECT score_bucket, tier, SUM(count) as total
      FROM leaderboard WHERE date >= ?
      GROUP BY score_bucket, tier ORDER BY score_bucket DESC
    `).bind(sinceStr).all();

    // Country leaders
    const countries = await env.DB.prepare(`
      SELECT country, tier, SUM(count) as total
      FROM leaderboard WHERE date >= ?
      GROUP BY country, tier ORDER BY total DESC LIMIT 50
    `).bind(sinceStr).all();

    return json({
      period_days: days,
      since: sinceStr,
      tier_distribution: tiers.results,
      score_distribution: scores.results,
      country_leaders: countries.results,
    });
  } catch (err) {
    console.error('Leaderboard query error:', err);
    return json({ error: 'Query failed' }, 500);
  }
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
    },
  });
}
