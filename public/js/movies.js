'use strict';

/**
 * ============================================================================
 * CloudDriveLeech - Movies & TV Shows Hub Engine (2026 Ultra Edition)
 * ============================================================================
 * 
 * Architecture & Responsibilities:
 *  - SECTION 1: DOM Elements & Interface Selectors
 *  - SECTION 2: State Variables & Configuration
 *  - SECTION 3: Shared Helpers & URL Formatters (PixelDrain, MEGA, GDrive, Bad Links)
 *  - SECTION 4: Storage & Watchlist Management (Safe Quota Engine)
 *  - SECTION 5: Search Input & Live Suggestions Dropdown
 *  - SECTION 6: Hero Banner Carousel & Auto-Slider Engine
 *  - SECTION 7: Multi-Source Movie Search & Data Aggregation
 *  - SECTION 8: Movie Grid Rendering & Sorting Algorithms
 *  - SECTION 9: Modal Lifecycle & Stream Resolution Engine
 *  - SECTION 10: Movie Details UI & Quality Card Handlers (Play / Leech / DL)
 * ============================================================================
 */

// ============================================================================
// SECTION 1: DOM Elements & Interface Selectors
// ============================================================================
const DEFAULT_POSTER_SVG = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 450' width='300' height='450'%3E%3Cdefs%3E%3ClinearGradient id='bg' x1='0%25' y1='0%25' x2='100%25' y2='100%25'%3E%3Cstop offset='0%25' stop-color='%231a1a2e'/%3E%3Cstop offset='100%25' stop-color='%2316213e'/%3E%3C/linearGradient%3E%3C/defs%3E%3Crect width='100%25' height='100%25' fill='url(%23bg)'/%3E%3Cg transform='translate(100, 160)' fill='%23e94560'%3E%3Cpath d='M80 20 L20 20 A10 10 0 0 0 10 30 L10 90 A10 10 0 0 0 20 100 L80 100 A10 10 0 0 0 90 90 L90 30 A10 10 0 0 0 80 20 Z M45 40 L65 60 L45 80 Z' fill='%23ff4757' opacity='0.8'/%3E%3C/g%3E%3Ctext x='50%25' y='320' fill='%23ffffff' font-family='sans-serif' font-size='16' font-weight='bold' text-anchor='middle' opacity='0.7'%3ECinema HD%3C/text%3E%3C/svg%3E";

// Search Command Deck
const movieSearchInput = document.getElementById('movieSearchInput');
const btnSearchMovie = document.getElementById('btnSearchMovie');
const btnClearMovieSearch = document.getElementById('btnClearMovieSearch');
const movieLiveSuggestions = document.getElementById('movieLiveSuggestions');
const movieSearchStatus = document.getElementById('movieSearchStatus');
const movieSearchStatusText = document.getElementById('movieSearchStatusText');

// Grid & Loading
const movieLoading = document.getElementById('movieLoading');
const movieSkeletonGrid = document.getElementById('movieSkeletonGrid');
const movieGrid = document.getElementById('movieGrid');
const movieSectionTitle = document.getElementById('movieSectionTitle');
const movieResultsCount = document.getElementById('movieResultsCount');

// Hero Spotlight Carousel
const movieHeroBanner = document.getElementById('movieHeroBanner');
const heroProgressBar = document.getElementById('heroProgressBar');
const heroSlideIndex = document.getElementById('heroSlideIndex');
const heroSubBadge = document.getElementById('heroSubBadge');
const heroQualityBadge = document.getElementById('heroQualityBadge');
const heroDotsContainer = document.getElementById('heroDotsContainer');
const heroPosterThumb = document.getElementById('heroPosterThumb');
const heroRatingBadge = document.getElementById('heroRatingBadge');
const heroBackdropImg = document.getElementById('heroBackdropImg');
const heroMovieTitle = document.getElementById('heroMovieTitle');
const heroMovieSynopsis = document.getElementById('heroMovieSynopsis');

// Movie Details Modal
const movieModal = document.getElementById('movieModal');
const btnCloseMovieModal = document.getElementById('btnCloseMovieModal');
const modalBackdropImg = document.getElementById('modalBackdropImg');
const modalMoviePoster = document.getElementById('modalMoviePoster');
const modalMovieTitle = document.getElementById('modalMovieTitle');
const modalSourceBadge = document.getElementById('modalSourceBadge');
const modalMovieYear = document.getElementById('modalMovieYear');
const modalMovieRating = document.getElementById('modalMovieRating');
const modalMovieSynopsis = document.getElementById('modalMovieSynopsis');
const modalQualitiesList = document.getElementById('modalQualitiesList');
const btnToggleSynopsis = document.getElementById('btnToggleSynopsis');
const btnModalRefreshLinks = document.getElementById('btnModalRefreshLinks');


// ============================================================================
// SECTION 2: State Variables & Configuration
// ============================================================================
let currentPortalFilter = 'all';
let rawMovieResults = [];
let featuredHeroMovie = null;
let movieSearchDebounceTimer = null;
let currentSearchInFlight = null;
let currentSearchQuery = '';
let activeMovieRequestId = 0;
let currentActiveMovieUrl = null;
let currentMovieAlgo = 'trending';
let isAppFirstLoaded = false;

// Hero Carousel State
let heroCarouselMovies = [];
let heroCurrentIndex = 0;
let heroSlideTimer = null;
let heroProgressAnim = null;
const HERO_SLIDE_DURATION = 5000; // 5 seconds per slide
let heroProgressStartTime = 0;
let isHeroPaused = false;
let heroTouchStartX = 0;
let heroTouchStartY = 0;

// Search Dropdown State
let selectedSuggestionIndex = -1;
let placeholderIndex = 0;
let charIndex = 0;
let isDeleting = false;
let typingSpeed = 65;

const searchPlaceholders = [
  "Search 2026 Movies & Sinhala Subtitles...",
  "Search Marvel, DC, Deadpool & Wolverine...",
  "Search Netflix, HBO Max & 4K Ultra HD...",
  "Search Action, Horror & Sci-Fi Cinema...",
  "Search Tamil, Hindi & South Indian Hits...",
  "Search 1080p Sinhala Subtitle Films..."
];


// ============================================================================
// SECTION 3: Shared Helpers & URL Formatters (DRY)
// ============================================================================

/**
 * Normalizes movie candidates into one canonical model:
 * id, title, url, thumbnail, source, duration, quality, language, publishedAt, metadata
 * Enforces real metadata only; unknown fields default to '--' or omit without fabrication.
 */
function sanitizeMovieItem(m) {
  if (!m || typeof m !== 'object') return null;

  // 1. Poster & Thumbnail sanitization
  let poster = m.poster || m.thumbnail || '';
  if (poster.includes('unsplash') || poster.startsWith('data:image')) poster = '';
  if (poster.startsWith('http://')) poster = poster.replace('http://', 'https://');

  let thumbnail = m.thumbnail || poster || '';
  if (thumbnail.includes('unsplash') || thumbnail.startsWith('data:image')) thumbnail = '';
  if (thumbnail.startsWith('http://')) thumbnail = thumbnail.replace('http://', 'https://');

  const link = m.link || m.url || '';
  const cleanId = m.id || (window.getMovieCanonicalKey ? window.getMovieCanonicalKey(m) : link) || String(Math.random());

  // 2. Strict normalized fields
  const title = (m.title || 'Untitled Movie').trim();
  const source = m.source || 'Sinhalasub';
  const duration = m.duration || m.runtime || '--';
  const quality = m.quality || '--';
  const language = m.language || (source.toLowerCase().includes('sinhala') ? 'Sinhala Sub' : '--');
  const publishedAt = m.publishedAt || m.year || '--';
  const rating = m.rating || m.score || '--';

  return {
    id: cleanId,
    title: title,
    url: link,
    link: link,
    thumbnail: thumbnail,
    poster: poster,
    source: source,
    duration: duration,
    quality: quality,
    language: language,
    publishedAt: publishedAt,
    year: publishedAt !== '--' ? publishedAt : '',
    rating: rating !== '--' ? rating : '',
    metadata: m.metadata || {},
    imdb: m.imdb || '',
    tmdb: m.tmdb || '',
    isNetflix: Boolean(m.isNetflix || source.toLowerCase().includes('netflix')),
    isTv: Boolean(m.isTv)
  };
}

/**
 * Normalizes movie canonical key across all sources (Sinhalasub, SubLK, Baiscope, PirateLK, YTS, Netflix)
 * Incorporates portal source to strictly prevent cross-website overwrites and duplicate-name link collisions.
 */
window.getMovieCanonicalKey = function (m) {
  if (!m) return '';

  const rawSrc = (m.source || (m.isNetflix ? 'netflix' : 'sinhalasub')).toLowerCase();
  let portalTag = 'sinhalasub';
  if (rawSrc.includes('netflix') || rawSrc.includes('cinejoy') || m.isNetflix || (m.link || '').includes('cinejoy.to')) portalTag = 'netflix';
  else if (rawSrc.includes('pirate')) portalTag = 'piratelk';
  else if (rawSrc.includes('yts')) portalTag = 'yts';
  else if (rawSrc.includes('baiscope')) portalTag = 'baiscope';
  else if ((rawSrc.includes('sub.lk') || rawSrc.includes('sublk')) && !rawSrc.includes('sinhalasub')) portalTag = 'sublk';
  else if (rawSrc.includes('sinhala')) portalTag = 'sinhalasub';

  // Netflix / Cinejoy canonical key: Use official TMDB/Cinejoy ID if available
  if (portalTag === 'netflix') {
    const idMatch = (m.link || '').match(/\/(?:movie|tv|title)\/(\d+)/);
    if (idMatch) return 'cinejoy_' + idMatch[1];
    if (m.tmdb) return 'cinejoy_' + m.tmdb;
    if (m.id && (m.id.startsWith('netflix_') || m.id.startsWith('cinejoy_'))) {
      return 'cinejoy_' + m.id.replace(/^(netflix|cinejoy)_/, '');
    }
    const nKey = (m.title || '').toLowerCase().replace(/[^a-z0-9]/g, '');
    if (nKey) return 'cinejoy_' + nKey;
  }

  const titleStr = (m.title || '').toLowerCase()
    .replace(/\b(sinhala\s*sub|sinhala\s*subtitles?|sinhala|sub|subtitles?|web-?dl|bluray|hdrip|hdcam|cam|1080p|720p|480p|x264|x265|hevc|season\s*\d+|ep\s*\d+|episode\s*\d+)\b/gi, '')
    .replace(/[^\w\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  const yearMatch = (m.year || '').toString().trim() || ((m.title || '').match(/\b(19\d\d|20\d\d)\b/) || [''])[0];

  if (titleStr) {
    return `${portalTag}__${titleStr.replace(/\s+/g, '_')}__${yearMatch}`.trim();
  }

  if (m.link) {
    try {
      const u = new URL(m.link);
      return `${portalTag}__${(u.hostname + u.pathname).replace(/\/+$/, '').toLowerCase()}`;
    } catch (_) {
      return `${portalTag}__${m.link.toLowerCase().trim().replace(/\/+$/, '')}`;
    }
  }
  return '';
};

// 🏛️ Dedicated In-Memory Storage Buckets for the 6 Portals
const portalMovieStorage = {
  all: [],
  sinhalasub: [],
  sublk: [],
  baiscope: [],
  piratelk: [],
  yts: [],
  netflix: []
};
window.portalMovieStorage = portalMovieStorage;

function partitionMoviesIntoPortals(movies = []) {
  portalMovieStorage.all = movies;
  portalMovieStorage.sinhalasub = [];
  portalMovieStorage.sublk = [];
  portalMovieStorage.baiscope = [];
  portalMovieStorage.piratelk = [];
  portalMovieStorage.yts = [];
  portalMovieStorage.netflix = [];

  const seenNetflix = new Set();
  movies.forEach(m => {
    if (!m) return;
    const s = (m.source || '').toLowerCase();
    const l = (m.link || '').toLowerCase();
    if (l.includes('sinhalasub')) {
      portalMovieStorage.sinhalasub.push(m);
    } else if (l.includes('baiscope')) {
      portalMovieStorage.baiscope.push(m);
    } else if ((l.includes('sub.lk') || l.includes('sublk') || s.includes('sub.lk')) && !l.includes('sinhalasub')) {
      portalMovieStorage.sublk.push(m);
    } else if (l.includes('piratelk') || s.includes('piratelk')) {
      portalMovieStorage.piratelk.push(m);
    } else if (l.includes('yts.') || l.includes('yts.mx') || s.includes('yts')) {
      portalMovieStorage.yts.push(m);
    }
    if (s.includes('netflix') || s.includes('cinejoy') || m.isNetflix || l.includes('netflix.com') || l.includes('cinejoy.to')) {
      const key = window.getMovieCanonicalKey ? window.getMovieCanonicalKey(m) : (m.id || m.title);
      if (key && !seenNetflix.has(key)) {
        seenNetflix.add(key);
        portalMovieStorage.netflix.push(m);
      } else if (!key) {
        portalMovieStorage.netflix.push(m);
      }
    }
  });
}
window.partitionMoviesIntoPortals = partitionMoviesIntoPortals;

// Dynamic Netflix Live Catalog & TV Series Caches
let netflixLiveCollection = [];
const SERIES_DATABASE = {};



/**
 * Dynamic Series Episodes Fetcher (Cinemeta API with LocalStorage Caching)
 */
async function fetchSeriesEpisodes(imdbId) {
  if (!imdbId) return null;
  if (SERIES_DATABASE[imdbId]) return SERIES_DATABASE[imdbId];

  try {
    // Purge legacy corrupted cache if any
    localStorage.removeItem('cdl_series_episodes_' + imdbId);
    const cached = localStorage.getItem('cdl_series_episodes_v2_' + imdbId);
    if (cached) {
      const parsed = JSON.parse(cached);
      if (parsed && parsed.seasons && Object.keys(parsed.seasons).length > 0) {
        return parsed;
      }
    }
  } catch (_) { }

  try {
    const res = await fetch(`https://v3-cinemeta.strem.io/meta/series/${imdbId}.json`);
    if (!res.ok) return null;
    const json = await res.json();
    const meta = json.meta;
    if (!meta || !meta.videos || meta.videos.length === 0) return null;

    const seasons = {};
    meta.videos.forEach(v => {
      // Cinemeta returns specials/minisodes under season 0.
      // In JS, 0 || 1 evaluates to 1 because 0 is falsy!
      // Explicitly check for valid positive season numbers (> 0).
      const rawSeason = (v.season !== undefined && v.season !== null && v.season !== '') ? Number(v.season) : 1;
      if (isNaN(rawSeason) || rawSeason <= 0) return; // Ignore Season 0 specials, minisodes, trailers
      const sNum = rawSeason;

      const rawEp = (v.number !== undefined && v.number !== null && v.number !== '')
        ? Number(v.number)
        : (v.episode !== undefined && v.episode !== null && v.episode !== '')
          ? Number(v.episode)
          : null;
      if (rawEp === null || isNaN(rawEp) || rawEp <= 0) return;
      const epNum = rawEp;

      if (!seasons[sNum]) seasons[sNum] = [];
      // Deduplicate by episode number
      if (seasons[sNum].some(item => item.episode === epNum)) return;

      seasons[sNum].push({
        episode: epNum,
        name: v.name || v.title || `Episode ${epNum}`,
        runtime: v.runtime ? `${v.runtime} min` : '50 min',
        thumbnail: v.thumbnail || meta.poster || DEFAULT_POSTER_SVG,
        overview: v.overview || v.description || 'Full HD episode stream available.'
      });
    });

    // Ensure all episodes in each season are sorted in ascending order (E1, E2, E3...)
    Object.keys(seasons).forEach(s => {
      seasons[s].sort((a, b) => a.episode - b.episode);
    });

    const seriesObj = {
      title: meta.name || 'TV Series',
      imdb: imdbId,
      seasons
    };

    try {
      localStorage.setItem('cdl_series_episodes_v2_' + imdbId, JSON.stringify(seriesObj));
    } catch (_) { }

    return seriesObj;
  } catch (err) {
    console.warn('[movies.js] Error fetching series episodes for', imdbId, err);
    return null;
  }
}

let currentSeriesData = null;
let currentSelectedSeason = 1;
let currentSelectedEpisode = 1;

/**
 * Sets up Season Tabs & Episode Cards in Movie Details Modal
 */
async function setupSeriesEpisodes(movie, details) {
  const modalSeriesSection = document.getElementById('modalSeriesSection');
  if (!modalSeriesSection) return;

  const titleLower = ((movie?.title || '') + ' ' + (details?.title || '')).toLowerCase();
  const isKnownSeries = titleLower.includes('squid game') ||
    titleLower.includes('wednesday') ||
    titleLower.includes('stranger things') ||
    titleLower.includes('money heist') ||
    titleLower.includes('all of us are dead') ||
    titleLower.includes('witcher') ||
    titleLower.includes('lupin') ||
    (titleLower.includes('peaky blinders') && titleLower.includes('season')) ||
    titleLower.includes('game of thrones') ||
    titleLower.includes('house of the dragon') ||
    titleLower.includes('breaking bad') ||
    titleLower.includes('the boys');

  const isTv = movie?.isTv || details?.isTv || isKnownSeries || /Season|Series|Episode|S\d+/i.test(movie?.title || '') || /Season|Series|Episode|S\d+/i.test(details?.title || '') || ((movie?.source || '').toLowerCase().includes('netflix') && isKnownSeries);

  if (!isTv) {
    modalSeriesSection.classList.add('hidden');
    return;
  }

  let imdbId = details?.imdb || movie?.imdb || (movie?.link?.match(/tt\d{7,8}/)?.[0]) || (details?.link?.match(/tt\d{7,8}/)?.[0]) || '';

  if (!imdbId) {
    const cleanT = (movie?.title || details?.title || '').replace(/\[.*?\]/g, '').replace(/\(.*?\)/g, '').replace(/season.*$/i, '').trim();
    try {
      const sRes = await fetch('https://v3-cinemeta.strem.io/catalog/series/top/search=' + encodeURIComponent(cleanT) + '.json').then(r => r.json());
      if (sRes?.metas?.[0]) {
        imdbId = sRes.metas[0].imdb_id || sRes.metas[0].id || '';
      }
    } catch (_) { }
  }

  let seriesData = null;
  if (imdbId) {
    seriesData = await fetchSeriesEpisodes(imdbId);
  }

  if (!seriesData || !seriesData.seasons || Object.keys(seriesData.seasons).length === 0) {
    modalSeriesSection.classList.add('hidden');
    return;
  }

  // Defensive sanitization: delete season 0 and deduplicate/sort
  delete seriesData.seasons[0];
  delete seriesData.seasons['0'];
  Object.keys(seriesData.seasons).forEach(s => {
    const seen = new Set();
    seriesData.seasons[s] = (seriesData.seasons[s] || []).filter(item => {
      if (seen.has(item.episode)) return false;
      seen.add(item.episode);
      return true;
    }).sort((a, b) => a.episode - b.episode);
  });

  currentSeriesData = seriesData;
  modalSeriesSection.classList.remove('hidden');

  const seasonNums = Object.keys(seriesData.seasons).map(Number).sort((a, b) => a - b);
  currentSelectedSeason = seasonNums[0] || 1;
  currentSelectedEpisode = 1;

  renderSeasonTabs(seasonNums, seriesData, movie, details);
  renderEpisodesList(currentSelectedSeason, seriesData, movie, details);
}

function renderSeasonTabs(seasonNums, seriesData, movie, details) {
  const modalSeasonTabs = document.getElementById('modalSeasonTabs');
  if (!modalSeasonTabs) return;
  modalSeasonTabs.innerHTML = '';

  seasonNums.forEach(sNum => {
    const btn = document.createElement('button');
    btn.className = `season-btn ${sNum === currentSelectedSeason ? 'active' : ''}`;
    btn.innerHTML = `<i class="fa-solid fa-folder-open"></i> Season ${sNum}`;
    btn.onclick = () => {
      currentSelectedSeason = sNum;
      currentSelectedEpisode = 1;
      modalSeasonTabs.querySelectorAll('.season-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      renderEpisodesList(sNum, seriesData, movie, details);
    };
    modalSeasonTabs.appendChild(btn);
  });
}

function renderEpisodesList(seasonNum, seriesData, movie, details) {
  const modalEpisodesList = document.getElementById('modalEpisodesList');
  const modalCurrentEpisodePill = document.getElementById('modalCurrentEpisodePill');
  if (!modalEpisodesList) return;
  modalEpisodesList.innerHTML = '';

  const episodes = seriesData.seasons[seasonNum] || [];
  if (episodes.length === 0) {
    modalEpisodesList.innerHTML = `<div style="text-align: center; color: #94a3b8; padding: 20px;">No episodes found for Season ${seasonNum}</div>`;
    return;
  }

  const tmdbId = seriesData.tmdb || movie?.tmdb || seriesData.imdb || movie?.imdb || '';

  episodes.forEach((ep, idx) => {
    const epNum = ep.episode || (idx + 1);
    const isSelected = (seasonNum === currentSelectedSeason && epNum === currentSelectedEpisode);

    const item = document.createElement('div');
    item.className = `episode-card-item ${isSelected ? 'active' : ''}`;

    const epStreamUrl = tmdbId
      ? `https://vidsrc2.ru/embed/tv/${tmdbId}/${seasonNum}/${epNum}`
      : `https://vidsrc2.ru/embed/tv/${seriesData.imdb || movie.imdb || tmdbId}/${seasonNum}/${epNum}`;
    const thumbUrl = ep.thumbnail || movie?.poster || DEFAULT_POSTER_SVG;

    item.innerHTML = `
      <div class="episode-thumb-wrap">
        <img src="${thumbUrl}" alt="S${seasonNum}E${epNum}" loading="lazy" referrerpolicy="no-referrer" onerror="this.onerror=null; this.src='${movie?.poster || DEFAULT_POSTER_SVG}';">
        <div class="episode-thumb-play">
          <i class="fa-solid fa-play"></i>
        </div>
      </div>
      <div class="episode-details-col">
        <div class="episode-title-row">
          <span class="episode-number-title">E${epNum} &bull; ${ep.name}</span>
          <span class="episode-runtime-tag"><i class="fa-solid fa-clock"></i> ${ep.runtime || '55 min'}</span>
        </div>
        <p class="episode-overview-snippet">${ep.overview || 'Tap to stream in 1080p FHD'}</p>
      </div>
      <button class="episode-action-btn" title="Stream Episode Now">
        <i class="fa-solid fa-play"></i>
        <span>Play</span>
      </button>
    `;

    const selectEpisodeAction = (shouldAutoPlay = false) => {
      currentSelectedEpisode = epNum;
      modalEpisodesList.querySelectorAll('.episode-card-item').forEach(el => el.classList.remove('active'));
      item.classList.add('active');

      if (modalCurrentEpisodePill) {
        modalCurrentEpisodePill.innerHTML = `Season ${seasonNum} &bull; Ep ${epNum}: ${ep.name}`;
      }

      // Update qualities section to point to this episode
      updateQualitiesForEpisode(movie, details, seasonNum, epNum, ep, tmdbId);

      if (shouldAutoPlay) {
        window.closeMovieModal();
        const primaryQuality = (details.qualities && details.qualities.length > 0) ? details.qualities[0] : null;
        const primaryStream = primaryQuality ? (primaryQuality.streamUrl || primaryQuality.downloadUrl) : epStreamUrl;
        const primaryType = primaryQuality?.type || (primaryStream.match(/\.(mp4|mkv|m3u8)(\?|$)/i) ? 'video' : 'embed');

        if (window.Capacitor?.Plugins?.NativePlayer?.playVideo) {
          const serversList = (details.qualities || []).map(q => ({
            quality: q.quality || '1080p FHD',
            provider: q.provider || 'VidSrc2 / Cinejoy',
            downloadUrl: q.downloadUrl || q.streamUrl || '',
            type: q.type || 'embed'
          }));
          window.Capacitor.Plugins.NativePlayer.playVideo({
            url: primaryStream,
            streamUrl: primaryStream,
            title: `${movie.title} - S${seasonNum} E${epNum}: ${ep.name}`,
            poster: thumbUrl,
            category: 'movies',
            serversJson: JSON.stringify(serversList),
            servers: serversList,
            isTv: true,
            seriesTitle: movie.title || details?.title || '',
            season: seasonNum,
            episode: epNum,
            tmdb: tmdbId || movie.tmdb || seriesData.tmdb || '',
            imdb: movie.imdb || seriesData.imdb || '',
            seriesData: seriesData || {},
            seriesJson: seriesData ? JSON.stringify(seriesData) : '{}'
          });
        } else {
          window.openPlayer({
            title: `${movie.title} - S${seasonNum} E${epNum}: ${ep.name}`,
            streamUrl: primaryStream,
            embedUrl: primaryStream,
            downloadUrl: primaryStream,
            type: primaryType,
            isZip: false,
            allQualities: details.qualities || [],
            imdb: movie.imdb || seriesData.imdb || '',
            tmdb: tmdbId || movie.tmdb || seriesData.tmdb || '',
            season: seasonNum,
            episode: epNum,
            isTv: true,
            poster: thumbUrl,
            provider: primaryQuality?.provider || 'Cinejoy VIP Stream'
          });
        }
      }
    };

    item.onclick = (e) => {
      if (e.target.closest('.episode-action-btn')) {
        selectEpisodeAction(true);
      } else {
        selectEpisodeAction(false);
      }
    };

    modalEpisodesList.appendChild(item);
  });

  const firstEp = episodes[0];
  if (firstEp && modalCurrentEpisodePill) {
    modalCurrentEpisodePill.innerHTML = `Season ${seasonNum} &bull; Ep ${firstEp.episode || 1}: ${firstEp.name}`;
    updateQualitiesForEpisode(movie, details, seasonNum, firstEp.episode || 1, firstEp, tmdbId);
  }
}

function updateQualitiesForEpisode(movie, details, season, episode, epObj, tmdbId) {
  if (!details) return;
  const targetTmdb = tmdbId || movie?.tmdb || '';
  const targetImdb = movie?.imdb || details?.imdb || '';
  const titleQuery = encodeURIComponent(movie?.title || 'Series');

  details.activeSeason = season;
  details.activeEpisode = episode;
  details.activeEpName = epObj?.name || `Episode ${episode}`;

  const sVidSrc2 = `https://vidsrc2.ru/embed/tv/${targetTmdb || targetImdb || titleQuery}/${season}/${episode}`;
  const sCinejoy = targetTmdb
    ? `https://cinejoy.to/watch/tv/${targetTmdb}/${season}/${episode}`
    : (targetImdb ? `https://cinejoy.to/watch/tv/${targetImdb}/${season}/${episode}` : `https://cinejoy.to/search`);
  const sMultiEmbed = `https://multiembed.mov/?video_id=${targetImdb || targetTmdb || titleQuery}&s=${season}&e=${episode}`;
  const isNetflix = (movie?.source || '').toLowerCase().includes('netflix') || (details?.source || '').toLowerCase().includes('netflix');

  if (isNetflix) {
    details.qualities = [
      {
        quality: '🌟 Server 1: VidSrc2 Pro – 1080p Full HD • Multi-Audio',
        provider: 'VidSrc2 Pro',
        downloadUrl: sVidSrc2,
        streamUrl: sVidSrc2,
        size: epObj?.runtime ? `Runtime: ${epObj.runtime}` : '1080p FHD Multi-Audio',
        type: 'embed',
        serverName: 'VidSrc2 Pro'
      },
      {
        quality: '🚀 Server 2: Cinejoy VIP – 4K UHD / 1080p Stream',
        provider: 'Cinejoy VIP',
        downloadUrl: sCinejoy,
        streamUrl: sCinejoy,
        size: epObj?.runtime ? `Runtime: ${epObj.runtime}` : '4K UHD / 1080p FHD',
        type: 'embed',
        serverName: 'Cinejoy VIP'
      }
    ];
  } else {
    details.qualities = [
      {
        quality: '🌟 Server 1: VidSrc2 Pro – 1080p Full HD • Multi-Audio (සිංහල/English/හින්දි) සහ Multi-Language Subtitles',
        provider: 'VidSrc2 VIP Multi-Audio & Subtitles',
        downloadUrl: sVidSrc2,
        streamUrl: sVidSrc2,
        size: epObj?.runtime ? `Runtime: ${epObj.runtime}` : '1080p FHD Multi-Audio',
        type: 'embed',
        serverName: 'VidSrc2 Pro'
      },
      {
        quality: '🚀 Server 2: Cinejoy VIP – 4K UHD / 1080p Stream',
        provider: 'Cinejoy VIP',
        downloadUrl: sCinejoy,
        streamUrl: sCinejoy,
        size: epObj?.runtime ? `Runtime: ${epObj.runtime}` : '4K UHD / 1080p FHD',
        type: 'embed',
        serverName: 'Cinejoy VIP'
      }
    ];
  }

  if (window.renderQualitiesListOnly) {
    window.renderQualitiesListOnly(details.qualities, details, movie);
  }
}

/**
 * Returns CSS badge class for a given portal source
 */
function getSourceBadgeClass(source = '') {
  const s = String(source || '').toLowerCase();
  if (s.includes('netflix') || s.includes('cinejoy')) return 'portal-netflix';
  if (s.includes('pirate')) return 'portal-piratelk';
  if (s.includes('yts')) return 'portal-yts';
  if ((s.includes('sub.lk') || s.includes('sublk')) && !s.includes('sinhalasub')) return 'portal-sublk';
  if (s.includes('baiscope')) return 'portal-baiscope';
  return 'portal-sinhalasub';
}

/**
 * PixelDrain URL Helpers
 */
function getPixelDrainId(url) {
  if (!url || typeof url !== 'string') return null;
  return url.match(/pixeldrain\.com\/(?:u|l|api\/file|e)\/([a-zA-Z0-9_-]+)/)?.[1] || null;
}

function getPixelDrainApiUrl(url) {
  const id = getPixelDrainId(url);
  return id ? `https://pixeldrain.com/api/file/${id}` : url;
}

function getPixelDrainDownloadUrl(url) {
  const id = getPixelDrainId(url);
  return id ? `https://pixeldrain.com/api/file/${id}?download` : url;
}

function getPixelDrainEmbedUrl(url) {
  const id = getPixelDrainId(url);
  return id ? `https://pixeldrain.com/e/${id}` : url;
}

/**
 * Converts MEGA share links into secure embed URLs
 */
function getMegaEmbedUrl(url) {
  if (!url || typeof url !== 'string') return url;
  if (url.includes('/embed')) return url;
  const match = url.match(/mega\.(?:nz|io|co\.nz)\/(?:file|embed)\/([a-zA-Z0-9#_-]+)/);
  if (match && match[1]) return `https://mega.nz/embed/${match[1]}`;
  return url.replace('/file/', '/embed/').replace('/#!', '/embed#!').replace('/#', '/embed#!').replace('#!', 'embed#!');
}

/**
 * Converts Google Drive preview/share link to high-speed binary download endpoint
 */
function getGoogleDriveDownloadUrl(url) {
  if (!url || typeof url !== 'string') return url;
  const match = url.match(/(?:drive\.google\.com|drive\.usercontent\.google\.com)\/(?:file\/d\/|open\?id=|uc\?id=|download\?id=)([a-zA-Z0-9_-]+)/);
  if (match && match[1]) {
    return `https://drive.usercontent.google.com/download?id=${match[1]}&export=download&authuser=0`;
  }
  return url;
}

/**
 * Converts Google Drive share/view/uc link to standard embed preview player
 */
function getGoogleDrivePreviewUrl(url) {
  if (!url || typeof url !== 'string') return url;
  const match = url.match(/(?:drive\.google\.com|drive\.usercontent\.google\.com)\/(?:file\/d\/|open\?id=|uc\?id=|download\?id=)([a-zA-Z0-9_-]+)/)
    || url.match(/(?:file\/d\/|open\?id=|uc\?id=|id=)([a-zA-Z0-9_-]+)/);
  if (match && match[1]) {
    return `https://drive.google.com/file/d/${match[1]}/preview`;
  }
  return url;
}

/**
 * Filters out Telegram ad links, gambling sites, or corrupt archive extensions
 */
function isBadStreamLink(url = '', text = '') {
  const u = (url || '').toLowerCase();
  const t = (text || '').toLowerCase();
  if (
    u.includes('t.me') || u.includes('telegram') || u.includes('telagram') || u.includes('tg://') ||
    u.includes('whatsapp') || u.includes('wa.me') || u.includes('facebook.com') || u.includes('fb.com') ||
    u.includes('twitter.com') || u.includes('instagram.com') || u.includes('tiktok.com') ||
    u.includes('affpa') || u.includes('1xbet') || u.includes('betway') || u.includes('adsterra') ||
    u.includes('monetag') || u.includes('admin-ajax.php') || u.includes('action=sub_download') ||
    u.includes('/tag/') || u.includes('/category/') || u.includes('/author/') ||
    u.includes('/comments/') || u.includes('/feed/') || u.includes('xmlrpc.php') ||
    u.includes('wp-content') || u.includes('wp-json') || u.includes('#mega-cat') ||
    u.includes('multiembed.mov') || u.includes('multiembed') || u.includes('vidlink.pro') ||
    u.includes('filespayout') || t.includes('filespayout') ||
    t.includes('බෙංගාලි') || t.includes('මලයාලම්') || t.includes('සිංහල හඬකැවූ') ||
    u.endsWith('.srt') || u.endsWith('.vtt') || u.endsWith('.sub')
  ) return true;

  if (u.endsWith('.zip') || u.endsWith('.rar')) {
    const isMovieRelease = u.includes('usersdrive') || u.includes('userdrive') || /720p|1080p|2160p|4k|webrip|bluray|fhd|hd|\bgb\b|\bmb\b|movie|film|lama|පිටපත/i.test(t);
    if (!isMovieRelease && (t.includes('subtitle') || t.includes('sub') || t.includes('උපසිරැසි'))) return true;
  }

  if (
    t.includes('telegram') || t.includes('telagram') || t.includes('join whatsapp') ||
    t.includes('1xbet') || t.includes('betway') || t.includes('join our channel') || t.includes('group link')
  ) return true;

  return false;
}

/**
 * Strips subtitle noise and tags from movie titles
 */
function formatMovieCardTitle(rawTitle = '') {
  if (!rawTitle) return 'Movie Title';
  let clean = rawTitle;
  if (/problem|advertisement|report|cancel/i.test(clean)) {
    clean = clean.replace(/^(Labeling problem|Video Problem|Sound Problem|Buffering|Advertisement).*$/i, '').trim();
  }
  clean = clean.split('|')[0].trim();
  clean = clean.split('–')[0].trim();
  clean = clean.split('—')[0].trim();
  clean = clean.replace(/Sinhala\s*Subtitles?/i, '').trim();
  clean = clean.replace(/සිංහල\s*උපසිර[ැසි]+/i, '').trim();
  clean = clean.replace(/Watch\s*Online/i, '').trim();
  clean = clean.replace(/\[.*?\]/g, '').trim();
  clean = clean.replace(/\s+/g, ' ');
  return clean || rawTitle;
}

function formatRatingNumber(rawRating = '', title = '') {
  if (rawRating) {
    const str = String(rawRating);
    const match = str.match(/([1-9]\.[0-9])/);
    if (match) return match[1];
    const num = parseFloat(str.replace(/[^0-9.]/g, ''));
    if (num >= 3.0 && num <= 10.0) return num.toFixed(1);
  }
  const cleanT = (title || 'Movie').replace(/sinhala\s*sub.*$/i, '').trim();
  const hash = cleanT.split('').reduce((acc, c) => acc + c.charCodeAt(0), 0);
  return (6.8 + (hash % 22) * 0.1).toFixed(1);
}

function formatRatingBadge(rawRating = '', title = '') {
  const score = formatRatingNumber(rawRating, title);
  return `<i class="fa-solid fa-star" style="color: #f5c518; margin-right: 3px; font-size: 0.85em;"></i>${score}`;
}

function extractMovieYear(movie) {
  const isNf = movie?.isNetflix ||
    (movie?.source || '').toLowerCase().includes('netflix') ||
    (movie?.link || '').includes('netflix.com') ||
    currentPortalFilter === 'netflix';
  if (isNf) {
    return '2026';
  }
  const match = (movie?.title || '').match(/\b(19\d\d|20\d\d)\b/);
  if (match) return match[1];
  if (movie?.year && /\d{4}/.test(movie.year)) {
    return movie.year.match(/\d{4}/)[0];
  }
  return '2026';
}

/**
 * Unified Provider Metadata Table (Replaces 110 lines of repetitive if-else code)
 */
const PROVIDER_METADATA_TABLE = [
  { match: /pixeldrain/i, label: 'PixelDrain (1 Gbps Direct Cloud)', icon: 'fa-hard-drive', color: '#2ed573' },
  { match: /drive\.google|gdrive/i, label: 'Google Drive High-Speed Cloud', icon: 'fa-brands fa-google-drive', color: '#34d399' },
  { match: /workers\.dev|cloudflare/i, label: 'Cloudflare Workers Direct Pipe', icon: 'fa-bolt', color: '#00f2fe' },
  { match: /mega(?:\.nz|\.io|\.co\.nz)?/i, label: 'MEGA High-Speed Cloud', icon: 'fa-cloud', color: '#ff4757' },
  { match: /usersdrive|userdrive/i, label: 'UsersDrive Direct Cloud', icon: 'fa-cloud', color: '#5352ed' },
  { match: /filespayout/i, label: 'FilesPayout Direct Cloud', icon: 'fa-cloud-arrow-down', color: '#20bf6b' },
  { match: /dlserver-01|cdn\.sinhalasub/i, label: 'DLServer-01 (1 Gbps Direct CDN)', icon: 'fa-bolt', color: '#ffd32a' },
  { match: /dlserver-02|ddl\./i, label: 'DLServer-02 Fast CDN Mirror', icon: 'fa-bolt', color: '#eccc68' },
  { match: /cineru(?:streams)?/i, label: 'Cineru Direct HLS Stream', icon: 'fa-play', color: '#00d2d3' },
  { match: /akirabox/i, label: 'AkiraBox High-Speed Server', icon: 'fa-box-archive', color: '#70a1ff' },
  { match: /sonic-cloud|cinesubz|zt-links/i, label: 'CineSubz Sonic-Cloud Pipe', icon: 'fa-cloud-bolt', color: '#a55eea' },
  { match: /baiscope/i, label: 'Baiscope High-Speed Cloud Mirror', icon: 'fa-film', color: '#ffa502' },
  { match: /gofile/i, label: 'GoFile Unlimited Cloud', icon: 'fa-cloud-arrow-down', color: '#00d2d3' },
  { match: /1fichier/i, label: '1Fichier Cloud Storage', icon: 'fa-server', color: '#ff6b81' },
  { match: /vidsrc2|vidlink/i, label: 'VidSrc2 Pro Multi-Audio (FHD)', icon: 'fa-bolt', color: '#00f2fe' },
  { match: /multiembed/i, label: 'MultiEmbed VIP Fast Cloud (4K/FHD)', icon: 'fa-server', color: '#2ed573' },
  { match: /vidsrc/i, label: 'VidSrc VIP High-Speed CDN', icon: 'fa-rocket', color: '#a55eea' },
  { match: /2embed/i, label: '2Embed CC Global CDN Mirror', icon: 'fa-gem', color: '#00d2d3' },
  { match: /cinejoy direct|4k cinejoy|shegu\.st|4khdhub/i, label: 'Cinejoy Direct Cloud (4K/1080p)', icon: 'fa-bolt', color: '#95FF50' },
  { match: /cinejoy/i, label: 'Cinejoy VIP Cloud Stream (4K/FHD)', icon: 'fa-play', color: '#95FF50' },
  { match: /netflix|netmirror/i, label: 'Netflix VIP Cloud Mirror', icon: 'fa-n', color: '#e50914' }
];

function getProviderDisplayMeta(provider = '', url = '', label = '') {
  const combined = `${provider} ${url} ${label}`.trim();
  for (const entry of PROVIDER_METADATA_TABLE) {
    if (entry.match.test(combined)) {
      const customLabel = (provider && provider.length > 2 && !provider.toLowerCase().includes('fast cloud server') && !provider.toLowerCase().includes('sub.lk'))
        ? provider
        : entry.label;
      return {
        label: customLabel,
        icon: entry.icon,
        color: entry.color
      };
    }
  }
  return {
    label: provider || 'Direct Cloud Mirror',
    icon: 'fa-server',
    color: '#00f2fe'
  };
}


// ============================================================================
// SECTION 4: Storage & Watchlist Management (Safe Quota Engine)
// ============================================================================

/**
 * Safe LocalStorage setter with QuotaExceeded eviction
 */
function safeSetLocalStorage(key, value) {
  try {
    localStorage.setItem(key, value);
  } catch (err) {
    console.warn('[Storage] QuotaExceededError, evicting oldest temporary caches...');
    try {
      const keys = Object.keys(localStorage);
      const evictable = keys.filter(k =>
        k.startsWith('cdl_stream_res_') ||
        k.startsWith('cdl_details_res_') ||
        (k.startsWith('cdl_movie_cache_') && !k.endsWith('2026'))
      );
      for (const k of evictable.slice(0, 35)) {
        localStorage.removeItem(k);
      }
      localStorage.setItem(key, value);
    } catch (_) { }
  }
}

// Watchlist Operations
const WATCHLIST_STORAGE_KEY = 'cdl_movie_watchlist_v1';

function getWatchlist() {
  try {
    const raw = localStorage.getItem(WATCHLIST_STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch (_) { return []; }
}

function saveWatchlist(list) {
  try {
    safeSetLocalStorage(WATCHLIST_STORAGE_KEY, JSON.stringify(list.slice(0, 200)));
    updateWatchlistPillCount();
  } catch (_) { }
}

function isMovieInWatchlist(movie) {
  if (!movie) return false;
  const list = getWatchlist();
  const targetKey = window.getMovieCanonicalKey ? window.getMovieCanonicalKey(movie) : (movie.link || movie.title);
  return list.some(m => {
    const k = window.getMovieCanonicalKey ? window.getMovieCanonicalKey(m) : (m.link || m.title);
    return k && k === targetKey;
  });
}

function toggleMovieWatchlist(movie) {
  if (!movie) return false;
  let list = getWatchlist();
  const targetKey = window.getMovieCanonicalKey ? window.getMovieCanonicalKey(movie) : (movie.link || movie.title);
  const exists = list.some(m => {
    const k = window.getMovieCanonicalKey ? window.getMovieCanonicalKey(m) : (m.link || m.title);
    return k && k === targetKey;
  });

  if (exists) {
    list = list.filter(m => {
      const k = window.getMovieCanonicalKey ? window.getMovieCanonicalKey(m) : (m.link || m.title);
      return k !== targetKey;
    });
    saveWatchlist(list);
    if (window.showToast) window.showToast(`Removed "${formatMovieCardTitle(movie.title)}" from Watchlist`, 'info');
    return false;
  } else {
    list.unshift(sanitizeMovieItem({ ...movie, savedAt: Date.now() }));
    saveWatchlist(list);
    if (window.showToast) window.showToast(`❤️ Added "${formatMovieCardTitle(movie.title)}" to Watchlist!`, 'success');
    return true;
  }
}

function updateWatchlistPillCount() {
  const pill = document.getElementById('watchlistCountPill');
  if (pill) {
    const count = getWatchlist().length;
    pill.textContent = count;
  }
}

setTimeout(updateWatchlistPillCount, 100);

// Disk Caching Engine for Streams & Details (0 MB RAM, Pure Disk Storage)
const STREAM_DISK_CACHE_PREFIX = 'cdl_stream_res_';
const MOVIE_DETAILS_DISK_CACHE_PREFIX = 'cdl_details_res_';
const MOVIES_PERSISTENT_CACHE_PREFIX = 'cdl_movie_cache_v5_';

// Ephemeral Stream Detector: URLs that expire, have dynamic tokens, or need live quota failover
function isEphemeralStream(url) {
  if (!url || typeof url !== 'string') return true;
  const lower = url.toLowerCase();
  return lower.includes('workers.dev') ||
    lower.includes('shegu.st') ||
    lower.includes('cinejoy') ||
    lower.includes('netflix') ||
    lower.includes('cloudflarestorage.com') ||
    lower.includes('usersdrive.com') ||
    lower.includes('userdrive.org') ||
    lower.includes('token=') ||
    lower.includes('expire=') ||
    lower.includes('signature=') ||
    lower.includes('hash=') ||
    lower.includes('&st=') ||
    lower.includes('?st=') ||
    lower.includes('drive.google.com/uc?') ||
    lower.includes('downloadquotaexceeded');
}

// 🧹 One-time boot purge: Clear all legacy stream caches and dynamic movie detail caches from localStorage
(function purgeStaleStreamDiskCaches() {
  try {
    const toDelete = [];
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (!k) continue;
      if (k.startsWith(STREAM_DISK_CACHE_PREFIX) || k.startsWith(MOVIE_DETAILS_DISK_CACHE_PREFIX)) {
        toDelete.push(k);
      }
    }
    toDelete.forEach(k => localStorage.removeItem(k));
    if (toDelete.length > 0) {
      console.log(`[Disk Cache Boot Purge] Cleaned ${toDelete.length} stale/broken stream caches.`);
    }
  } catch (_) { }
})();

window.getStreamDiskCache = function (url) {
  if (!url || isEphemeralStream(url)) return null;
  try {
    const raw = localStorage.getItem(STREAM_DISK_CACHE_PREFIX + url);
    if (!raw) return null;
    const entry = JSON.parse(raw);
    // Ephemeral or short-lived: Max 15 minutes TTL (900,000 ms) for static streams
    if (entry.t && (Date.now() - entry.t > 900000)) {
      localStorage.removeItem(STREAM_DISK_CACHE_PREFIX + url);
      return null;
    }
    if (isEphemeralStream(entry.data?.streamUrl)) {
      localStorage.removeItem(STREAM_DISK_CACHE_PREFIX + url);
      return null;
    }
    return entry.data;
  } catch (_) { return null; }
};

window.setStreamDiskCache = function (url, data) {
  if (!url || !data || !data.streamUrl) return;
  // NEVER cache ephemeral, worker, tokenized, or quota-sensitive streams!
  if (isEphemeralStream(url) || isEphemeralStream(data.streamUrl)) {
    return;
  }
  try {
    safeSetLocalStorage(STREAM_DISK_CACHE_PREFIX + url, JSON.stringify({ t: Date.now(), data }));
  } catch (_) { }
};

window.removeStreamDiskCache = function (url) {
  if (!url) return;
  try {
    localStorage.removeItem(STREAM_DISK_CACHE_PREFIX + url);
    const cleanUrl = url.split('?')[0];
    localStorage.removeItem(STREAM_DISK_CACHE_PREFIX + cleanUrl);
  } catch (_) { }
};

window.getMovieDetailsDiskCache = function (keyOrUrl, source = '') {
  if (!keyOrUrl) return null;
  const sLower = (source || '').toLowerCase();
  const kLower = keyOrUrl.toLowerCase();
  // Never serve cached movie details for dynamic portals (Cinejoy, Netflix, Shegu)
  if (sLower.includes('netflix') || sLower.includes('cinejoy') || kLower.includes('cinejoy') || kLower.includes('netflix') || kLower.includes('shegu')) {
    return null;
  }
  const sTag = source ? source.toLowerCase().replace(/[^a-z0-9]/g, '') + '__' : '';
  const fullKey = sTag + keyOrUrl;
  try {
    const raw = localStorage.getItem(MOVIE_DETAILS_DISK_CACHE_PREFIX + fullKey) || localStorage.getItem(MOVIE_DETAILS_DISK_CACHE_PREFIX + keyOrUrl);
    if (!raw) return null;
    const entry = JSON.parse(raw);
    if (entry.t && (Date.now() - entry.t > 86400000)) {
      localStorage.removeItem(MOVIE_DETAILS_DISK_CACHE_PREFIX + fullKey);
      return null;
    }
    return entry.data;
  } catch (_) { return null; }
};

window.setMovieDetailsDiskCache = function (keyOrUrl, data, source = '') {
  if (!keyOrUrl || !data) return;
  const sLower = (source || '').toLowerCase();
  const kLower = keyOrUrl.toLowerCase();
  // NEVER cache movie details for dynamic stream providers
  if (sLower.includes('netflix') || sLower.includes('cinejoy') || kLower.includes('cinejoy') || kLower.includes('netflix') || kLower.includes('shegu')) {
    return;
  }
  const sTag = source ? source.toLowerCase().replace(/[^a-z0-9]/g, '') + '__' : '';
  const fullKey = sTag + keyOrUrl;
  try {
    safeSetLocalStorage(MOVIE_DETAILS_DISK_CACHE_PREFIX + fullKey, JSON.stringify({ t: Date.now(), data }));
  } catch (_) { }
};

window.removeMovieDetailsDiskCache = function (keyOrUrl, source = '') {
  if (!keyOrUrl) return;
  const sTag = source ? source.toLowerCase().replace(/[^a-z0-9]/g, '') + '__' : '';
  const fullKey = sTag + keyOrUrl;
  try {
    localStorage.removeItem(MOVIE_DETAILS_DISK_CACHE_PREFIX + fullKey);
    localStorage.removeItem(MOVIE_DETAILS_DISK_CACHE_PREFIX + keyOrUrl);
    const cleanUrl = keyOrUrl.split('?')[0];
    localStorage.removeItem(MOVIE_DETAILS_DISK_CACHE_PREFIX + cleanUrl);
    Object.keys(localStorage).forEach(k => {
      if (k.startsWith(STREAM_DISK_CACHE_PREFIX) && (k.includes(encodeURIComponent(keyOrUrl)) || k.includes(keyOrUrl) || (cleanUrl && k.includes(cleanUrl)))) {
        localStorage.removeItem(k);
      }
    });
  } catch (_) { }
};

window.clearStreamDiskCache = function () {
  try {
    let cleared = 0;
    Object.keys(localStorage).forEach(k => {
      if (k.startsWith(STREAM_DISK_CACHE_PREFIX) || k.startsWith(MOVIE_DETAILS_DISK_CACHE_PREFIX)) {
        localStorage.removeItem(k);
        cleared++;
      }
    });
    if (cleared > 0) {
      console.log(`[Disk Cache] Cleared ${cleared} cached stream & detail links.`);
    }
  } catch (_) { }
};

function getPersistentMovieCache(key) {
  try {
    const raw = localStorage.getItem(MOVIES_PERSISTENT_CACHE_PREFIX + key);
    return raw ? JSON.parse(raw) : null;
  } catch (_) { return null; }
}

function setPersistentMovieCache(key, data) {
  try {
    if (!data || !Array.isArray(data) || data.length === 0) return;
    safeSetLocalStorage(MOVIES_PERSISTENT_CACHE_PREFIX + key, JSON.stringify(data.slice(0, 600)));
  } catch (_) { }
}

window.clearMovieCache = function () {
  window.clearStreamDiskCache();
  try {
    Object.keys(localStorage).forEach(k => {
      if (k.startsWith(MOVIES_PERSISTENT_CACHE_PREFIX)) localStorage.removeItem(k);
    });
  } catch (_) { }
};


// ============================================================================
// SECTION 5: Search Input & Live Suggestions Dropdown
// ============================================================================

function hideLiveSuggestions() {
  if (movieLiveSuggestions) {
    movieLiveSuggestions.classList.add('hidden');
    movieLiveSuggestions.innerHTML = '';
    selectedSuggestionIndex = -1;
  }
}

function renderLiveSuggestions(query = '') {
  if (!movieLiveSuggestions || !query || query.length < 2) {
    hideLiveSuggestions();
    return;
  }

  const cleanQ = query.toLowerCase().replace(/[^a-z0-9]/g, ' ').trim();
  const qWords = cleanQ.split(/\s+/).filter(w => w.length > 1);

  const candidates = [...rawMovieResults];
  const diskTrending = getPersistentMovieCache('2026');
  if (Array.isArray(diskTrending)) candidates.push(...diskTrending);

  // Also harvest cached items from previous searches in localStorage
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (k && k.startsWith(MOVIES_PERSISTENT_CACHE_PREFIX)) {
        const item = getPersistentMovieCache(k.replace(MOVIES_PERSISTENT_CACHE_PREFIX, ''));
        if (Array.isArray(item)) candidates.push(...item.slice(0, 15));
      }
    }
  } catch (_) { }

  const seen = new Set();
  const matched = [];

  for (const m of candidates) {
    if (!m || !m.title) continue;
    const titleL = m.title.toLowerCase();
    const isMatch = titleL.includes(cleanQ) || qWords.some(w => titleL.includes(w));
    if (isMatch && !seen.has(m.link || m.title)) {
      seen.add(m.link || m.title);
      matched.push(m);
      if (matched.length >= 6) break;
    }
  }

  if (matched.length === 0) {
    movieLiveSuggestions.innerHTML = `
      <div class="suggestion-item suggestion-web-trigger" style="padding: 10px 12px; cursor: pointer;">
        <i class="fa-solid fa-satellite-dish" style="color: var(--accent-cyan); font-size: 1.1rem; margin-right: 6px;"></i>
        <div class="suggestion-info">
          <div class="suggestion-title">Search "<b>${query}</b>" across all websites</div>
          <div class="suggestion-meta"><span>Sinhalasub &bull; Baiscope &bull; Sub.lk &bull; PirateLK &bull; YTS &bull; Netflix</span></div>
        </div>
        <i class="fa-solid fa-bolt" style="color: var(--accent-cyan); margin-left: auto;"></i>
      </div>
    `;
    movieLiveSuggestions.classList.remove('hidden');
    movieLiveSuggestions.querySelector('.suggestion-web-trigger')?.addEventListener('click', () => {
      hideLiveSuggestions();
      window.searchMovies(query, true);
    });
    return;
  }

  movieLiveSuggestions.innerHTML = matched.map((m, idx) => `
    <div class="suggestion-item" data-idx="${idx}">
      <img src="${m.poster || DEFAULT_POSTER_SVG}" alt="Poster" class="suggestion-thumb" onerror="this.src='${DEFAULT_POSTER_SVG}'">
      <div class="suggestion-info">
        <div class="suggestion-title">${formatMovieCardTitle(m.title)}</div>
        <div class="suggestion-meta">
          <span class="badge ${getSourceBadgeClass(m.source)}">${m.source || 'Sinhalasub'}</span>
          <span><i class="fa-solid fa-calendar"></i> ${extractMovieYear(m)}</span>
          <span style="color: #ffb800;">${formatRatingBadge(m.rating)}</span>
        </div>
      </div>
    </div>
  `).join('') + `
    <div class="suggestion-item suggestion-web-footer" style="border-top: 1px solid rgba(255,255,255,0.08); margin-top: 4px; padding-top: 8px; cursor: pointer;">
      <i class="fa-solid fa-satellite-dish" style="color: var(--accent-cyan); font-size: 0.9rem; margin-right: 6px;"></i>
      <span style="font-size: 0.78rem; color: var(--accent-cyan); font-weight: 600;">Search "${query}" on all 6 connected websites &rarr;</span>
    </div>
  `;

  movieLiveSuggestions.classList.remove('hidden');

  movieLiveSuggestions.querySelectorAll('.suggestion-item:not(.suggestion-web-footer)').forEach((item, idx) => {
    item.addEventListener('click', () => {
      const movie = matched[idx];
      if (movie) {
        if (movieSearchInput) movieSearchInput.value = formatMovieCardTitle(movie.title);
        hideLiveSuggestions();
        window.openMovieModal(movie);
      }
    });
  });

  movieLiveSuggestions.querySelector('.suggestion-web-footer')?.addEventListener('click', () => {
    hideLiveSuggestions();
    window.searchMovies(query, true);
  });
}

// Search Input Listeners
btnSearchMovie?.addEventListener('click', () => {
  clearTimeout(movieSearchDebounceTimer);
  hideLiveSuggestions();
  const val = movieSearchInput ? movieSearchInput.value.trim() : '';
  window.searchMovies(val || '2026', true);
});

movieSearchInput?.addEventListener('input', () => {
  const val = movieSearchInput.value.trim();
  if (val.length > 0) {
    btnClearMovieSearch?.classList.remove('hidden');
  } else {
    btnClearMovieSearch?.classList.add('hidden');
  }

  renderLiveSuggestions(val);

  clearTimeout(movieSearchDebounceTimer);
  movieSearchDebounceTimer = setTimeout(() => {
    if (val.length >= 2) {
      window.searchMovies(val, true);
    } else if (val.length === 0) {
      window.searchMovies('2026', true);
    }
  }, 400);
});

movieSearchInput?.addEventListener('keydown', (e) => {
  const items = movieLiveSuggestions?.querySelectorAll('.suggestion-item') || [];

  if (e.key === 'ArrowDown') {
    e.preventDefault();
    if (items.length > 0) {
      selectedSuggestionIndex = (selectedSuggestionIndex + 1) % items.length;
      items.forEach((it, idx) => it.classList.toggle('selected', idx === selectedSuggestionIndex));
      items[selectedSuggestionIndex]?.scrollIntoView({ block: 'nearest' });
    }
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    if (items.length > 0) {
      selectedSuggestionIndex = (selectedSuggestionIndex - 1 + items.length) % items.length;
      items.forEach((it, idx) => it.classList.toggle('selected', idx === selectedSuggestionIndex));
      items[selectedSuggestionIndex]?.scrollIntoView({ block: 'nearest' });
    }
  } else if (e.key === 'Enter') {
    e.preventDefault();
    clearTimeout(movieSearchDebounceTimer);
    if (selectedSuggestionIndex >= 0 && items[selectedSuggestionIndex]) {
      items[selectedSuggestionIndex].click();
    } else {
      hideLiveSuggestions();
      const val = movieSearchInput.value.trim();
      window.searchMovies(val || '2026', true);
    }
  } else if (e.key === 'Escape') {
    hideLiveSuggestions();
  }
});

document.addEventListener('click', (e) => {
  if (!e.target.closest('.search-capsule-2026')) {
    hideLiveSuggestions();
  }
});

// Shortcut '/' to focus search bar
document.addEventListener('keydown', (e) => {
  if (e.key === '/' && !['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName)) {
    e.preventDefault();
    const movieTab = document.querySelector('[data-tab="movies"]');
    if (movieTab) movieTab.click();
    setTimeout(() => {
      movieSearchInput?.focus();
      movieSearchInput?.select();
    }, 50);
  }
});

btnClearMovieSearch?.addEventListener('click', () => {
  if (movieSearchInput) movieSearchInput.value = '';
  btnClearMovieSearch?.classList.add('hidden');
  hideLiveSuggestions();
  const statusBar = document.getElementById('movieSearchStatus');
  if (statusBar) statusBar.classList.add('hidden');
  document.querySelectorAll('#view-movies .category-pills-2026 .tag-pill, #view-movies .quick-tags .tag-pill').forEach((p, idx) => {
    p.classList.toggle('active', idx === 0);
  });
  window.searchMovies('2026', true);
});

// Category & Quick Filter Pills
document.querySelectorAll('#view-movies .category-pills-2026 .tag-pill, #view-movies .quick-tags .tag-pill').forEach(pill => {
  pill.addEventListener('click', () => {
    document.querySelectorAll('#view-movies .category-pills-2026 .tag-pill, #view-movies .quick-tags .tag-pill').forEach(p => p.classList.remove('active'));
    pill.classList.add('active');
    const q = pill.dataset.q;
    if (q === 'watchlist') {
      if (movieSearchInput) movieSearchInput.value = '';
      btnClearMovieSearch?.classList.add('hidden');
      clearTimeout(movieSearchDebounceTimer);
      window.searchMovies('watchlist');
      return;
    }
    if (movieSearchInput) movieSearchInput.value = q;
    btnClearMovieSearch?.classList.remove('hidden');
    clearTimeout(movieSearchDebounceTimer);
    window.searchMovies(q);
  });
});

document.querySelectorAll('.hot-chip, .suggest-chip').forEach(chip => {
  chip.addEventListener('click', () => {
    const q = chip.dataset.q;
    if (movieSearchInput) movieSearchInput.value = q;
    btnClearMovieSearch?.classList.remove('hidden');
    clearTimeout(movieSearchDebounceTimer);

    document.querySelectorAll('#view-movies .category-pills-2026 .tag-pill').forEach(p => {
      p.classList.toggle('active', p.dataset.q === q);
    });

    window.searchMovies(q);
  });
});

// Animated Placeholder Typing Effect
function typeSearchPlaceholder() {
  if (!movieSearchInput || document.activeElement === movieSearchInput || movieSearchInput.value.length > 0) {
    setTimeout(typeSearchPlaceholder, 1500);
    return;
  }

  const currentText = searchPlaceholders[placeholderIndex];
  if (isDeleting) {
    movieSearchInput.setAttribute('placeholder', currentText.substring(0, charIndex - 1));
    charIndex--;
    typingSpeed = 30;
  } else {
    movieSearchInput.setAttribute('placeholder', currentText.substring(0, charIndex + 1));
    charIndex++;
    typingSpeed = 65;
  }

  if (!isDeleting && charIndex === currentText.length) {
    typingSpeed = 2000;
    isDeleting = true;
  } else if (isDeleting && charIndex === 0) {
    isDeleting = false;
    placeholderIndex = (placeholderIndex + 1) % searchPlaceholders.length;
    typingSpeed = 350;
  }

  setTimeout(typeSearchPlaceholder, typingSpeed);
}

setTimeout(typeSearchPlaceholder, 1200);

// Portal Source Filters (All, Sinhalasub, SubLK, Baiscope, PirateLK, YTS, Netflix)
document.querySelectorAll('.portal-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.portal-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    currentPortalFilter = btn.dataset.portal || 'all';

    const qText = (movieSearchInput?.value || '').trim();
    const isSpecific = qText && qText !== '2026' && qText !== 'trending' && qText !== 'popular';

    if (movieSectionTitle) {
      if (currentPortalFilter === 'netflix') {
        movieSectionTitle.innerHTML = isSpecific
          ? `<i class="fa-solid fa-n" style="color: #e50914; font-weight: 900;"></i> 🍿 Netflix Results for "${qText}"`
          : `<i class="fa-solid fa-n" style="color: #e50914; font-weight: 900;"></i> 🍿 Netflix VIP Releases & Originals`;
      } else if (currentPortalFilter === 'all') {
        movieSectionTitle.innerHTML = isSpecific
          ? `<i class="fa-solid fa-film" style="color: var(--accent-cyan);"></i> Multi-Web Results for "${qText}"`
          : `<i class="fa-solid fa-fire" style="color: var(--accent-magenta);"></i> 🔥 Trending 2026 Cinema Releases`;
      } else {
        const pName = currentPortalFilter.charAt(0).toUpperCase() + currentPortalFilter.slice(1);
        movieSectionTitle.innerHTML = isSpecific
          ? `<i class="fa-solid fa-film" style="color: var(--accent-cyan);"></i> ${pName} Results for "${qText}"`
          : `<i class="fa-solid fa-film" style="color: var(--accent-cyan);"></i> ${pName} Portal Releases`;
      }
    }

    if (!rawMovieResults || rawMovieResults.length === 0) {
      window.searchMovies(qText || '2026', true);
    } else if (currentPortalFilter === 'netflix') {
      const netflixCount = (rawMovieResults || []).filter(m => (m.source || '').toLowerCase().includes('netflix') || m.isNetflix || (m.link || '').includes('netflix.com') || (m.link || '').includes('cinejoy.to')).length;
      if (netflixCount < 40 && !isSpecific) {
        window.searchMovies('netflix', true);
      } else {
        filterAndRenderMovies();
      }
    } else {
      filterAndRenderMovies();
    }
  });
});


// ============================================================================
// SECTION 6: Hero Banner Carousel & Auto-Slider Engine
// ============================================================================

function updateHeroCarousel(movies = []) {
  if (!movies || movies.length === 0) return;
  const validMovies = movies.filter(m => m.poster && m.poster.startsWith('http'));
  heroCarouselMovies = (validMovies.length >= 3 ? validMovies : movies).slice(0, 10);
  heroCurrentIndex = 0;

  renderHeroDots();
  renderHeroSlide(0);
  startHeroAutoSlide();
}

function updateHeroBanner(movie) {
  if (Array.isArray(movie)) {
    updateHeroCarousel(movie);
  } else if (movie) {
    if (heroCarouselMovies.length === 0) {
      updateHeroCarousel([movie]);
    }
  }
}

function renderHeroDots() {
  if (!heroDotsContainer) return;
  heroDotsContainer.innerHTML = '';
  heroCarouselMovies.forEach((_, idx) => {
    const dot = document.createElement('div');
    dot.className = `hero-dot ${idx === heroCurrentIndex ? 'active' : ''}`;
    dot.title = `Movie ${idx + 1}`;
    dot.addEventListener('click', (e) => {
      e.stopPropagation();
      goToHeroSlide(idx);
    });
    heroDotsContainer.appendChild(dot);
  });
}

function updateHeroDots(activeIndex) {
  if (!heroDotsContainer) return;
  const dots = heroDotsContainer.querySelectorAll('.hero-dot');
  dots.forEach((dot, idx) => {
    dot.classList.toggle('active', idx === activeIndex);
  });
}

function renderHeroSlide(index) {
  if (!heroCarouselMovies || heroCarouselMovies.length === 0) return;
  const movie = heroCarouselMovies[index] || heroCarouselMovies[0];
  featuredHeroMovie = movie;

  if (heroSlideIndex) heroSlideIndex.textContent = (index + 1);
  if (heroMovieTitle) {
    heroMovieTitle.textContent = (movie.title || 'Featured Movie').replace(/\|.*$/g, '').replace(/Sinhala Subtitles.*$/i, '').trim();
  }
  if (heroMovieSynopsis) {
    const src = movie.source || 'Sinhalasub';
    const quality = movie.quality || '4K UHD / 1080p';
    heroMovieSynopsis.textContent = `Tap anywhere to stream in ${quality} or leech to Google Drive. Portal: ${src}`;
  }
  if (heroQualityBadge) {
    heroQualityBadge.textContent = movie.quality || '4K UHD';
  }
  if (heroRatingBadge) {
    const rating = movie.rating || movie.score || '7.8';
    heroRatingBadge.innerHTML = `<i class="fa-solid fa-star"></i> ${rating}`;
  }

  const posterUrl = (movie.poster && !movie.poster.includes('unsplash'))
    ? movie.poster.replace('/w185/', '/original/').replace('/w300/', '/original/')
    : DEFAULT_POSTER_SVG;

  if (heroPosterThumb) {
    heroPosterThumb.classList.add('fade-out');
    setTimeout(() => {
      heroPosterThumb.setAttribute('referrerpolicy', 'no-referrer');
      heroPosterThumb.src = posterUrl;
      heroPosterThumb.classList.remove('fade-out');
    }, 120);
  }

  if (heroBackdropImg) {
    heroBackdropImg.classList.add('fade-out');
    setTimeout(() => {
      heroBackdropImg.setAttribute('referrerpolicy', 'no-referrer');
      heroBackdropImg.src = posterUrl;
      heroBackdropImg.classList.remove('fade-out');
    }, 180);
  }

  updateHeroDots(index);
}

function startHeroAutoSlide() {
  stopHeroAutoSlide();
  if (heroCarouselMovies.length <= 1) return;

  heroProgressStartTime = Date.now();
  animateHeroProgressBar();

  heroSlideTimer = setTimeout(() => {
    if (!isHeroPaused) {
      nextHeroSlide();
    } else {
      startHeroAutoSlide();
    }
  }, HERO_SLIDE_DURATION);
}

function animateHeroProgressBar() {
  if (heroProgressAnim) cancelAnimationFrame(heroProgressAnim);

  function step() {
    if (!isHeroPaused && heroProgressBar) {
      const elapsed = Date.now() - heroProgressStartTime;
      const pct = Math.min(100, (elapsed / HERO_SLIDE_DURATION) * 100);
      heroProgressBar.style.width = `${pct}%`;
    }
    if (Date.now() - heroProgressStartTime < HERO_SLIDE_DURATION) {
      heroProgressAnim = requestAnimationFrame(step);
    }
  }
  heroProgressAnim = requestAnimationFrame(step);
}

function stopHeroAutoSlide() {
  if (heroSlideTimer) clearTimeout(heroSlideTimer);
  if (heroProgressAnim) cancelAnimationFrame(heroProgressAnim);
  if (heroProgressBar) heroProgressBar.style.width = '0%';
}

window.addEventListener('cloud:active-tab-changed', (event) => {
  if (event.detail?.tab === 'movies' && !document.hidden) {
    startHeroAutoSlide();
  } else {
    stopHeroAutoSlide();
  }
});

document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    stopHeroAutoSlide();
  } else if (window.state?.currentTab === 'movies') {
    startHeroAutoSlide();
  }
});

function nextHeroSlide() {
  if (heroCarouselMovies.length === 0) return;
  heroCurrentIndex = (heroCurrentIndex + 1) % heroCarouselMovies.length;
  renderHeroSlide(heroCurrentIndex);
  startHeroAutoSlide();
}

function prevHeroSlide() {
  if (heroCarouselMovies.length === 0) return;
  heroCurrentIndex = (heroCurrentIndex - 1 + heroCarouselMovies.length) % heroCarouselMovies.length;
  renderHeroSlide(heroCurrentIndex);
  startHeroAutoSlide();
}

function goToHeroSlide(index) {
  if (index < 0 || index >= heroCarouselMovies.length) return;
  heroCurrentIndex = index;
  renderHeroSlide(heroCurrentIndex);
  startHeroAutoSlide();
}

movieHeroBanner?.addEventListener('click', () => {
  if (featuredHeroMovie) {
    window.openMovieModal(featuredHeroMovie);
  }
});

movieHeroBanner?.addEventListener('touchstart', (e) => {
  isHeroPaused = true;
  if (e.touches && e.touches.length > 0) {
    heroTouchStartX = e.touches[0].clientX;
    heroTouchStartY = e.touches[0].clientY;
  }
}, { passive: true });

movieHeroBanner?.addEventListener('touchend', (e) => {
  isHeroPaused = false;
  if (e.changedTouches && e.changedTouches.length > 0) {
    const diffX = e.changedTouches[0].clientX - heroTouchStartX;
    const diffY = e.changedTouches[0].clientY - heroTouchStartY;

    if (Math.abs(diffX) > 40 && Math.abs(diffX) > Math.abs(diffY)) {
      if (diffX < 0) nextHeroSlide();
      else prevHeroSlide();
      return;
    }
  }
  startHeroAutoSlide();
}, { passive: true });

movieHeroBanner?.addEventListener('mouseenter', () => { isHeroPaused = true; });
movieHeroBanner?.addEventListener('mouseleave', () => { isHeroPaused = false; startHeroAutoSlide(); });


// ============================================================================
// SECTION 7: Multi-Source Movie Search & Data Aggregation
// ============================================================================

function updatePortalCounters(movies = []) {
  const counts = { all: movies.length, sinhalasub: 0, sublk: 0, baiscope: 0, piratelk: 0, yts: 0, netflix: 0 };
  movies.forEach(m => {
    if (!m) return;
    const s = (m.source || '').toLowerCase();
    const l = (m.link || '').toLowerCase();
    if (l.includes('sinhalasub')) counts.sinhalasub++;
    else if (l.includes('baiscope')) counts.baiscope++;
    else if ((l.includes('sub.lk') || l.includes('sublk') || s.includes('sub.lk')) && !l.includes('sinhalasub')) counts.sublk++;
    else if (l.includes('piratelk') || s.includes('piratelk')) counts.piratelk++;
    else if (l.includes('yts.') || l.includes('yts.mx') || s.includes('yts')) counts.yts++;
    if (s.includes('netflix') || (m.title || '').toLowerCase().includes('netflix') || m.isNetflix || l.includes('netflix.com') || l.includes('cinejoy.to')) counts.netflix++;
  });
  const netflixCount = counts.netflix || netflixLiveCollection.length;
  counts.netflix = netflixCount;

  const setCnt = (id, n, portalKey) => {
    const el = document.getElementById(id);
    if (el) el.textContent = n > 0 ? `(${n})` : '';
    const btn = document.querySelector(`.portal-btn[data-portal="${portalKey}"]`);
    if (btn) {
      if (n > 0) btn.classList.add('has-results');
      else btn.classList.remove('has-results');
    }
  };
  setCnt('countAll', counts.all, 'all');
  setCnt('countSinhalasub', counts.sinhalasub, 'sinhalasub');
  setCnt('countSublk', counts.sublk, 'sublk');
  setCnt('countBaiscope', counts.baiscope, 'baiscope');
  setCnt('countPiratelk', counts.piratelk, 'piratelk');
  setCnt('countYts', counts.yts, 'yts');
  setCnt('countNetflix', netflixCount, 'netflix');
  return counts;
}

/**
 * Searches and aggregates movie releases across native Kotlin and standalone engines in real-time
 */
window.searchMovies = async function (query, forceFresh = false, isBackgroundRefresh = false) {
  const cleanQ = (query || '2026').trim();
  const cacheKey = cleanQ.toLowerCase();
  const isSpecificSearch = cleanQ !== '2026' && cleanQ !== 'trending' && cleanQ !== 'popular';

  if (!forceFresh && currentSearchQuery === cleanQ && currentSearchInFlight) {
    return currentSearchInFlight;
  }

  // Watchlist View Mode
  if (cacheKey === 'watchlist') {
    if (movieSectionTitle) {
      movieSectionTitle.innerHTML = `<i class="fa-solid fa-heart" style="color: #ff4757;"></i> ❤️ My Saved Watchlist`;
    }
    if (movieSearchStatus) movieSearchStatus.classList.add('hidden');
    rawMovieResults = getWatchlist();
    updatePortalCounters(rawMovieResults);
    if (movieSkeletonGrid) movieSkeletonGrid.classList.add('hidden');
    if (rawMovieResults.length > 0) {
      updateHeroBanner(rawMovieResults[0]);
    }
    filterAndRenderMovies();
    return;
  }


  // Netflix Originals View Mode
  if (cacheKey === 'netflix') {
    if (movieSectionTitle) {
      movieSectionTitle.innerHTML = `<i class="fa-solid fa-n" style="color: #e50914; font-weight: 900;"></i> 🍿 Netflix Originals & VIP Releases`;
    }
    if (movieSearchStatus) movieSearchStatus.classList.add('hidden');
    document.querySelectorAll('.portal-btn').forEach(b => b.classList.toggle('active', b.dataset.portal === 'netflix'));
    currentPortalFilter = 'netflix';
    const netflixItems = (rawMovieResults || []).filter(m => (m.source || '').toLowerCase().includes('netflix') || m.isNetflix || (m.link || '').includes('netflix.com'));
    if (netflixItems.length > 0 && !forceFresh) {
      updatePortalCounters(rawMovieResults);
      if (movieSkeletonGrid) movieSkeletonGrid.classList.add('hidden');
      updateHeroBanner(netflixItems[0]);
      filterAndRenderMovies();
      return;
    }
  }

  // When a user searches for a specific movie, reset portal filter to "All Portals" so all websites' hits show
  if (isSpecificSearch && currentPortalFilter !== 'all' && cleanQ !== 'watchlist') {
    currentPortalFilter = 'all';
    document.querySelectorAll('.portal-btn').forEach(b => b.classList.toggle('active', b.dataset.portal === 'all'));
  }

  if (movieSectionTitle) {
    if (!isSpecificSearch) {
      movieSectionTitle.innerHTML = `<i class="fa-solid fa-fire" style="color: var(--accent-cyan);"></i> 2026 Trending Cinema Releases`;
    } else {
      movieSectionTitle.innerHTML = `<i class="fa-solid fa-film" style="color: var(--accent-cyan);"></i> Multi-Web Results for "${cleanQ}"`;
    }
  }

  // 📡 Real-Time Multi-Web Scanning Status Bar
  if (isSpecificSearch && movieSearchStatus && movieSearchStatusText) {
    movieSearchStatus.classList.remove('hidden', 'success');
    movieSearchStatusText.innerHTML = `<i class="fa-solid fa-satellite-dish fa-fade"></i> Searching across <b>Sinhalasub, Baiscope, Sub.lk, PirateLK, YTS, Netflix</b> for "<b>${cleanQ}</b>"...`;
  } else if (movieSearchStatus) {
    movieSearchStatus.classList.add('hidden');
  }

  // ⚡ Instant 0ms Launch: Render cached movies from localStorage OR Instant Seed
  const cachedData = !forceFresh ? getPersistentMovieCache(cacheKey) : null;
  let hasInstantRendered = false;

  if (cachedData && Array.isArray(cachedData) && cachedData.length > 0) {
    hasInstantRendered = true;
    rawMovieResults = cachedData.map(sanitizeMovieItem);
    partitionMoviesIntoPortals(rawMovieResults);
    updatePortalCounters(rawMovieResults);
    if (movieSkeletonGrid) movieSkeletonGrid.classList.add('hidden');
    updateHeroCarousel(rawMovieResults);
    filterAndRenderMovies();

    if (!forceFresh && !isBackgroundRefresh && !isSpecificSearch) {
      return;
    }
  }

  const hasExistingItems = (rawMovieResults && rawMovieResults.length > 0) || hasInstantRendered;
  if ((window.isAppOffline || !navigator.onLine) && !hasExistingItems) {
    if (movieSkeletonGrid) movieSkeletonGrid.classList.add('hidden');
    if (movieLoading) movieLoading.classList.add('hidden');
    if (movieSearchStatus) movieSearchStatus.classList.add('hidden');
    if (movieGrid) {
      movieGrid.innerHTML = `
        <div class="empty-state" style="grid-column: 1 / -1; padding: 40px 20px; text-align: center;">
          <i class="fa-solid fa-plane-slash" style="font-size: 2.8rem; color: var(--accent-cyan); margin-bottom: 12px;"></i>
          <h3>Offline Mode Active</h3>
          <p style="color: var(--text-secondary); font-size: 0.85rem; max-width: 400px; margin: 0 auto 16px;">
            Internet is required to discover new releases. Your saved Watchlist and downloaded movies remain fully playable offline.
          </p>
          <button class="btn btn-secondary" onclick="window.switchTab ? window.switchTab('downloads') : null" style="display: inline-flex; align-items: center; gap: 8px;">
            <i class="fa-solid fa-circle-down"></i> Open Downloads
          </button>
        </div>
      `;
    }
    if (movieResultsCount) movieResultsCount.textContent = 'Offline Mode';
    return;
  }

  if (!hasExistingItems && !isBackgroundRefresh) {
    if (movieSkeletonGrid) movieSkeletonGrid.classList.remove('hidden');
    if (movieLoading) movieLoading.classList.add('hidden');
    if (movieGrid) movieGrid.innerHTML = '';
  } else if (movieSkeletonGrid) {
    movieSkeletonGrid.classList.add('hidden');
  }

  const thisRequestId = ++activeMovieRequestId;
  currentSearchQuery = cleanQ;
  currentSearchInFlight = (async () => {
    try {
      let results = [];

      // ⚡ 1. Direct Kotlin Native Extractor (100% On-Device Kotlin)
      if (window.Capacitor?.Plugins?.NativeExtractor?.searchMovies) {
        try {
          const nRes = await window.Capacitor.Plugins.NativeExtractor.searchMovies({ query: cleanQ, page: 1 });
          if (nRes && Array.isArray(nRes.results) && nRes.results.length > 0) {
            results = nRes.results;
          }
        } catch (err) {
          console.warn('[Kotlin Native Engine] searchMovies fallback:', err);
        }
      }

      // ⚡ 2. Standalone Client-Side Scraper Fallback (Queries all 7 portals)
      if (results.length === 0 && window.StandaloneEngine?.searchMovies) {
        try {
          results = await window.StandaloneEngine.searchMovies(cleanQ);
        } catch (_) { }
      }

      // 🛡️ Request ID Guard: If a newer search request was initiated while waiting, discard stale results!
      if (thisRequestId !== activeMovieRequestId) {
        return;
      }

      if (movieSkeletonGrid) movieSkeletonGrid.classList.add('hidden');

      if (results && results.length > 0) {
        const sanitizedNew = results.map(sanitizeMovieItem).filter(Boolean);

        // 🛡️ Smart Feed Merge for broad feeds vs dedicated search queries
        if (!isSpecificSearch || isBackgroundRefresh) {
          const getKey = (m) => (window.getMovieCanonicalKey ? window.getMovieCanonicalKey(m) : ((m.source || '') + '_' + (m.link || m.title || '')).toLowerCase().trim());
          const previousKeys = new Set((rawMovieResults || []).map(getKey).filter(Boolean));
          const newlyAdded = sanitizedNew.filter(m => {
            const k = getKey(m);
            return k && !previousKeys.has(k);
          });

          const combined = [...sanitizedNew];
          const seenKeys = new Set(sanitizedNew.map(getKey).filter(Boolean));
          if (Array.isArray(rawMovieResults)) {
            for (const oldM of rawMovieResults) {
              const k = getKey(oldM);
              if (k && !seenKeys.has(k)) {
                seenKeys.add(k);
                combined.push(oldM);
              }
            }
          }
          rawMovieResults = combined.slice(0, 1000);

          if (newlyAdded.length > 0) {
            console.log(`[Smart Feed Merge] Added ${newlyAdded.length} new movies across 6 web sources!`);
            if (window.showToast) {
              window.showToast(`✨ ${newlyAdded.length} New Movie Uploads Added!`, 'success');
            }
          } else if (isBackgroundRefresh && window.showToast) {
            window.showToast('✅ Movies feed is up to date', 'info');
          }
        } else {
          rawMovieResults = sanitizedNew;
        }

        partitionMoviesIntoPortals(rawMovieResults);
        setPersistentMovieCache(cacheKey, rawMovieResults);
        const counts = updatePortalCounters(rawMovieResults);
        updateHeroCarousel(rawMovieResults);
        filterAndRenderMovies();

        // Update Search Status Bar with live result counts across websites
        if (isSpecificSearch && movieSearchStatus && movieSearchStatusText) {
          movieSearchStatus.classList.add('success');
          const portalsFound = Object.keys(counts).filter(k => k !== 'all' && counts[k] > 0).length;
          movieSearchStatusText.innerHTML = `✨ Found <b>${rawMovieResults.length} movies</b> across <b>${portalsFound} websites</b> for "${cleanQ}"`;
          setTimeout(() => {
            movieSearchStatus.classList.add('hidden');
          }, 4500);
        }
      } else {
        if (rawMovieResults && rawMovieResults.length > 0) {
          if (movieResultsCount) movieResultsCount.textContent = `${rawMovieResults.length} Movies Available`;
          filterAndRenderMovies();
        } else {
          if (movieGrid) {
            movieGrid.innerHTML = `
              <div class="empty-state" style="grid-column: 1 / -1; padding: 40px 20px; text-align: center;">
                <i class="fa-solid fa-film" style="font-size: 2.8rem; color: var(--text-muted); margin-bottom: 12px;"></i>
                <h3>No Movies Found for "${cleanQ}"</h3>
                <p style="color: var(--text-secondary); font-size: 0.85rem;">None of the 6 connected websites returned results. Check your spelling or try another keyword.</p>
              </div>
            `;
          }
          if (movieResultsCount) movieResultsCount.textContent = '0 Movies Found';
        }

        if (isSpecificSearch && movieSearchStatus && movieSearchStatusText) {
          movieSearchStatus.classList.remove('success');
          movieSearchStatusText.innerHTML = `⚠️ No results found across websites for "${cleanQ}"`;
          setTimeout(() => {
            movieSearchStatus.classList.add('hidden');
          }, 4000);
        }
      }
    } catch (err) {
      if (movieSkeletonGrid) movieSkeletonGrid.classList.add('hidden');
      if (rawMovieResults && rawMovieResults.length > 0) {
        filterAndRenderMovies();
      } else {
        window.showToast('Network notice: ' + err.message, 'info');
      }
      if (movieSearchStatus) movieSearchStatus.classList.add('hidden');
    } finally {
      currentSearchInFlight = null;
    }
  })();
  return currentSearchInFlight;
};


// ============================================================================
// SECTION 8: Movie Grid Rendering & Sorting Algorithms
// ============================================================================

function getMovieTrendingScore(movie) {
  if (!movie) return 0;
  let score = 0;
  const title = (movie.title || '').toLowerCase();
  const rawRating = parseFloat((movie.rating || '').replace(/[^0-9.]/g, '')) || 7.0;
  const year = parseInt(extractMovieYear(movie) || '2026', 10);
  const src = (movie.source || '').toLowerCase();

  // 1. Rating score
  score += rawRating * 20;

  // 3. Year freshness boost
  if (year >= 2026) score += 60;
  else if (year === 2025) score += 40;
  else if (year === 2024) score += 20;

  // 4. Known Global & Catalog Blockbusters
  if (movie.trendingScore) score += movie.trendingScore;
  if (title.includes('grand theft auto') || title.includes('gta')) score += 700;
  else if (title.includes('dhamaal')) score += 650;
  else if (title.includes('squid game')) score += 600;
  else if (title.includes('stranger things')) score += 580;
  else if (title.includes('wednesday')) score += 560;
  else if (title.includes('money heist')) score += 540;
  else if (title.includes('peaky blinders')) score += 520;
  else if (title.includes('gladiator')) score += 500;
  else if (title.includes('deadpool')) score += 480;
  else if (title.includes('avatar')) score += 460;
  else if (title.includes('all of us are dead')) score += 440;
  else if (title.includes('queen of tears')) score += 420;
  else if (title.includes('arcane')) score += 400;
  else if (title.includes('one piece')) score += 390;
  else if (title.includes('cobra kai')) score += 380;
  else if (title.includes('witcher')) score += 370;
  else if (title.includes('lupin')) score += 360;
  else if (title.includes('breaking bad')) score += 350;
  else if (title.includes('black mirror')) score += 340;
  else if (title.includes('dune') || title.includes('oppenheimer') || title.includes('the boys')) score += 260;
  else {
    const popularCinemaKeywords = [
      'spider-man', 'batman', 'superman', 'godzilla', 'kong', 'venom', 'joker',
      'alien', 'transformers', 'fast & furious', 'john wick', 'mission impossible',
      'crash landing on you', 'house of the dragon', 'game of thrones',
      'beast', 'salaar', 'kalki', 'devara', 'pushpa', 'leo', 'jailer', 'jawan',
      'young sheldon', 'the good doctor', 'friends', 'no time to die', 'the meg'
    ];
    if (popularCinemaKeywords.some(kw => title.includes(kw))) {
      score += 180;
    }
  }

  // 4. Quality boost (4K UHD / 1080p FHD)
  if (/4k|2160p|fhd|1080p|uhd/i.test(title + ' ' + (movie.quality || ''))) {
    score += 25;
  }

  // 5. Source boost
  if (movie.isNetflix || src.includes('netflix')) {
    score += 30;
  } else if (src.includes('yts')) {
    score += 20;
  }

  return score;
}

function applyMovieCinemaAlgorithm(movies = [], algo = 'trending') {
  if (!Array.isArray(movies) || movies.length === 0) return [];
  const copy = [...movies];

  if (algo === 'trending') {
    // ⚡ Trending First Sorting: Hot releases, high IMDb ratings, 2026/2025 blockbusters & viral hits first!
    copy.sort((a, b) => {
      const scoreA = getMovieTrendingScore(a);
      const scoreB = getMovieTrendingScore(b);
      return scoreB - scoreA;
    });
    return copy;
  } else if (algo === 'latest') {
    return copy;
  } else if (algo === 'top-rated') {
    copy.sort((a, b) => {
      const rateA = parseFloat((a.rating || '').replace(/[^0-9.]/g, '')) || 7.0;
      const rateB = parseFloat((b.rating || '').replace(/[^0-9.]/g, '')) || 7.0;
      return rateB - rateA;
    });
  } else if (algo === '4k-master') {
    copy.sort((a, b) => {
      const is4kA = /4k|2160p|fhd|1080p/i.test(a.title + ' ' + (a.rating || ''));
      const is4kB = /4k|2160p|fhd|1080p/i.test(b.title + ' ' + (b.rating || ''));
      if (is4kA && !is4kB) return -1;
      if (!is4kA && is4kB) return 1;
      return 0;
    });
  } else if (algo === 'sinhala-sub') {
    copy.sort((a, b) => {
      const isSubA = (a.source || '').toLowerCase().includes('sinhala');
      const isSubB = (b.source || '').toLowerCase().includes('sinhala');
      if (isSubA && !isSubB) return -1;
      if (!isSubA && isSubB) return 1;
      return 0;
    });
  } else if (algo === 'surprise') {
    for (let i = copy.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [copy[i], copy[j]] = [copy[j], copy[i]];
    }
  }

  return copy;
}

function filterAndRenderMovies() {
  if (!movieGrid) return;
  movieGrid.innerHTML = '';

  let filtered = rawMovieResults;
  if (currentPortalFilter === 'sinhalasub') {
    filtered = (portalMovieStorage.sinhalasub && portalMovieStorage.sinhalasub.length > 0)
      ? portalMovieStorage.sinhalasub
      : rawMovieResults.filter(m => (m.link || '').includes('sinhalasub'));
  } else if (currentPortalFilter === 'sublk') {
    filtered = (portalMovieStorage.sublk && portalMovieStorage.sublk.length > 0)
      ? portalMovieStorage.sublk
      : rawMovieResults.filter(m => ((m.link || '').includes('sub.lk') || (m.link || '').includes('sublk') || (m.source || '').toLowerCase().includes('sub.lk')) && !(m.link || '').includes('sinhalasub'));
  } else if (currentPortalFilter === 'baiscope') {
    filtered = (portalMovieStorage.baiscope && portalMovieStorage.baiscope.length > 0)
      ? portalMovieStorage.baiscope
      : rawMovieResults.filter(m => (m.link || '').includes('baiscope') && !(m.link || '').includes('sinhalasub'));
  } else if (currentPortalFilter === 'piratelk') {
    filtered = (portalMovieStorage.piratelk && portalMovieStorage.piratelk.length > 0)
      ? portalMovieStorage.piratelk
      : rawMovieResults.filter(m => (m.link || '').includes('piratelk') || (m.source || '').toLowerCase().includes('piratelk'));
  } else if (currentPortalFilter === 'yts') {
    filtered = (portalMovieStorage.yts && portalMovieStorage.yts.length > 0)
      ? portalMovieStorage.yts
      : rawMovieResults.filter(m => (m.link || '').includes('yts.') || (m.link || '').includes('yts.mx') || (m.source || '').toLowerCase().includes('yts'));
  } else if (currentPortalFilter === 'netflix') {
    filtered = (portalMovieStorage.netflix && portalMovieStorage.netflix.length > 0)
      ? portalMovieStorage.netflix
      : (rawMovieResults || []).filter(m => (m.source || '').toLowerCase().includes('netflix') || (m.title || '').toLowerCase().includes('netflix') || m.isNetflix || (m.link || '').includes('netflix.com') || (m.link || '').includes('cinejoy.to'));
  }

  const curated = applyMovieCinemaAlgorithm(filtered, currentMovieAlgo);

  if (curated.length > 0) {
    updateHeroCarousel(curated);
  }

  if (movieResultsCount) {
    movieResultsCount.textContent = `Showing ${curated.length} Movies`;
  }

  if (curated.length === 0) {
    const pName = currentPortalFilter === 'all' ? 'Any Website' : currentPortalFilter.toUpperCase();
    movieGrid.innerHTML = `
      <div class="empty-state" style="grid-column: 1 / -1; padding: 40px 20px; text-align: center;">
        <i class="fa-solid fa-film" style="font-size: 2.8rem; color: var(--text-muted); margin-bottom: 12px;"></i>
        <h3>No Movies Found in "${pName}"</h3>
        <p style="color: var(--text-secondary); font-size: 0.85rem; margin-bottom: 12px;">This website did not have results for your search. Other websites might have it!</p>
        <button class="portal-btn active" style="margin: 0 auto; display: inline-flex;" onclick="document.querySelector('.portal-btn[data-portal=\\'all\\']')?.click();">
          <i class="fa-solid fa-globe"></i> View All Portals Results (${rawMovieResults.length})
        </button>
      </div>
    `;
    return;
  }

  const cardFragment = document.createDocumentFragment();

  curated.forEach(movie => {
    if (movie.poster && movie.poster.includes('unsplash')) movie.poster = '';
    if (movie.thumbnail && movie.thumbnail.includes('unsplash')) movie.thumbnail = '';

    const card = document.createElement('div');
    card.className = 'movie-card';
    if (movie.link) card.dataset.link = movie.link;
    if (movie.source) card.dataset.source = movie.source;
    card.dataset.title = movie.title || '';

    const srcLower = (movie.source || '').toLowerCase();
    const isNetflixItem = srcLower.includes('netflix') || srcLower.includes('cinejoy') || movie.isNetflix || (movie.link || '').includes('cinejoy.to') || (movie.id && (String(movie.id).startsWith('cinejoy_') || String(movie.id).startsWith('netflix_')));
    const portalClass = getSourceBadgeClass(isNetflixItem ? 'netflix' : movie.source);
    let portalLabel = 'Sinhalasub';
    if (isNetflixItem) portalLabel = 'Netflix';
    else if (srcLower.includes('pirate')) portalLabel = 'PirateLK';
    else if (srcLower.includes('yts')) portalLabel = 'YTS 4K';
    else if ((srcLower.includes('sub.lk') || srcLower.includes('sublk')) && !srcLower.includes('sinhalasub')) portalLabel = 'Sub.lk';
    else if (srcLower.includes('baiscope')) portalLabel = 'Baiscope';

    const subPillIcon = isNetflixItem ? 'fa-clock' : 'fa-closed-captioning';
    const subPillText = isNetflixItem ? (movie.runtime || 'VIP Stream') : 'Sinhala Sub';

    let posterUrl = (movie.poster && !movie.poster.includes('unsplash')) ? movie.poster : DEFAULT_POSTER_SVG;
    if (posterUrl.startsWith('http://')) {
      posterUrl = posterUrl.replace('http://', 'https://');
    }
    const displayTitle = formatMovieCardTitle(movie.title);
    const ratingDisplay = formatRatingBadge(movie.rating, movie.title);
    const yearDisplay = extractMovieYear(movie);
    const isSaved = isMovieInWatchlist(movie);

    card.innerHTML = `
      <div class="poster-wrapper">
        <img src="${posterUrl}" alt="${displayTitle}" referrerpolicy="no-referrer" loading="lazy" decoding="async" onerror="this.onerror=null; this.src='${DEFAULT_POSTER_SVG}';">
        <div class="poster-bottom-gradient"></div>
        <span class="source-badge ${portalClass}">${portalLabel}</span>
        <span class="rating-badge">${ratingDisplay}</span>

        <div class="card-quick-actions">
          <button class="btn-card-micro btn-card-stream" title="Instant Stream">
            <i class="fa-solid fa-play"></i> Stream
          </button>
          <button class="btn-card-micro btn-card-drive" title="1-Click Drive Leech">
            <i class="fa-solid fa-cloud-arrow-up"></i> Drive
          </button>
          <button class="btn-card-micro btn-card-bookmark ${isSaved ? 'active' : ''}" title="${isSaved ? 'Remove from Watchlist' : 'Save to Watchlist'}">
            <i class="${isSaved ? 'fa-solid' : 'fa-regular'} fa-heart"></i>
          </button>
        </div>
      </div>
      <div class="movie-info">
        <div class="movie-card-title" title="${movie.title}">${displayTitle}</div>
        <div class="movie-card-meta">
          <span class="sub-pill-tag"><i class="fa-solid ${subPillIcon}"></i> ${subPillText}</span>
          <span class="movie-year-pill">${yearDisplay}</span>
        </div>
      </div>
    `;

    card.addEventListener('click', (e) => {
      if (e.target.closest('.btn-card-bookmark')) return;
      window.openMovieModal(movie);
    });

    card.querySelector('.btn-card-bookmark')?.addEventListener('click', (e) => {
      e.stopPropagation();
      const btnBm = card.querySelector('.btn-card-bookmark');
      const nowSaved = toggleMovieWatchlist(movie);
      if (btnBm) {
        btnBm.classList.toggle('active', nowSaved);
        btnBm.innerHTML = `<i class="${nowSaved ? 'fa-solid' : 'fa-regular'} fa-heart"></i>`;
        btnBm.title = nowSaved ? 'Remove from Watchlist' : 'Save to Watchlist';
      }
      if (currentPortalFilter === 'watchlist' || movieSectionTitle?.textContent?.includes('Watchlist')) {
        rawMovieResults = getWatchlist();
      }
      filterAndRenderMovies();
    });

    cardFragment.appendChild(card);
  });

  movieGrid.appendChild(cardFragment);
}


// ============================================================================
// SECTION 9: Modal Lifecycle & Stream Resolution Engine
// ============================================================================

/**
 * Closes the Movie Details Modal and purges state to prevent card switching race conditions
 */
window.closeMovieModal = function (clearContext = true) {
  activeMovieRequestId++;
  currentActiveMovieUrl = null;
  window.state.selectedMovie = null;

  if (clearContext) {
    window._activeMovieReturnContext = null;
  }

  const modalSeriesSection = document.getElementById('modalSeriesSection');
  if (modalSeriesSection) modalSeriesSection.classList.add('hidden');
  const modalEpisodesList = document.getElementById('modalEpisodesList');
  if (modalEpisodesList) modalEpisodesList.innerHTML = '';
  const modalSeasonTabs = document.getElementById('modalSeasonTabs');
  if (modalSeasonTabs) modalSeasonTabs.innerHTML = '';

  if (modalQualitiesList) modalQualitiesList.innerHTML = '';
  if (modalMovieTitle) modalMovieTitle.textContent = '';
  if (modalMoviePoster) {
    modalMoviePoster.removeAttribute('src');
    modalMoviePoster.src = '';
  }
  if (modalBackdropImg) {
    modalBackdropImg.removeAttribute('src');
    modalBackdropImg.src = '';
  }
  if (modalMovieSynopsis) {
    modalMovieSynopsis.textContent = '';
    modalMovieSynopsis.classList.remove('expanded');
  }
  if (btnToggleSynopsis) btnToggleSynopsis.classList.add('hidden');

  const btnModalWatchlist = document.getElementById('btnModalWatchlist');
  if (btnModalWatchlist) btnModalWatchlist.onclick = null;
  const btnModalTrailer = document.getElementById('btnModalTrailer');
  if (btnModalTrailer) btnModalTrailer.onclick = null;
  const btnModalDirectDl = document.getElementById('btnModalDirectDl');
  if (btnModalDirectDl) btnModalDirectDl.onclick = null;

  if (movieModal) {
    movieModal.classList.add('hidden');
    if (window.modalStack) {
      window.modalStack = window.modalStack.filter(m => m !== movieModal);
    }
  }

  const otherOpenModals = Array.from(document.querySelectorAll('.modal-overlay:not(.hidden)'));
  if (otherOpenModals.length === 0) {
    document.body.classList.remove('modal-open');
    document.body.style.overflow = '';
    document.documentElement.style.overflow = '';
  }
};

function setupModalWatchlistAndTrailer(movie, details) {
  const targetMovie = movie || window.state.selectedMovie || {};
  const btnModalWatchlist = document.getElementById('btnModalWatchlist');
  if (btnModalWatchlist) {
    const isSaved = isMovieInWatchlist(targetMovie);
    btnModalWatchlist.innerHTML = `<i class="${isSaved ? 'fa-solid' : 'fa-regular'} fa-heart" style="${isSaved ? 'color: #ff4757;' : ''}"></i> ${isSaved ? 'In Watchlist' : 'Watchlist'}`;
    btnModalWatchlist.classList.toggle('active', isSaved);
    btnModalWatchlist.onclick = (e) => {
      e?.stopPropagation?.();
      const nowSaved = toggleMovieWatchlist(targetMovie);
      btnModalWatchlist.innerHTML = `<i class="${nowSaved ? 'fa-solid' : 'fa-regular'} fa-heart" style="${nowSaved ? 'color: #ff4757;' : ''}"></i> ${nowSaved ? 'In Watchlist' : 'Watchlist'}`;
      btnModalWatchlist.classList.toggle('active', nowSaved);
      if (currentPortalFilter === 'watchlist' || movieSectionTitle?.textContent?.includes('Watchlist')) {
        rawMovieResults = getWatchlist();
      }
      filterAndRenderMovies();
    };
  }


  const btnModalDirectDl = document.getElementById('btnModalDirectDl');
  if (btnModalDirectDl) {
    const isVipPortal = targetMovie?.isNetflix ||
      (targetMovie?.source || '').toLowerCase().includes('netflix') ||
      (targetMovie?.source || '').toLowerCase().includes('yts') ||
      (targetMovie?.source || '').toLowerCase().includes('cinejoy') ||
      targetMovie?.link?.includes('netflix') ||
      targetMovie?.link?.includes('cinejoy.to') ||
      targetMovie?.link?.includes('yts') ||
      (details?.qualities && details.qualities.some(q =>
        (q.downloadUrl || '').includes('cinejoy.to') ||
        (q.serverName || '').toLowerCase().includes('cinejoy') ||
        (q.provider || '').toLowerCase().includes('cinejoy') ||
        (q.quality || '').toLowerCase().includes('cinejoy') ||
        (q.serverName || '').toLowerCase().includes('vidsrc2')
      ));
    if (isVipPortal) {
      btnModalDirectDl.style.display = 'inline-flex';
      btnModalDirectDl.onclick = (e) => {
        e?.stopPropagation?.();
        window.openCinejoyDownloadPicker(targetMovie, details || window.currentMovieDetails);
      };
    } else {
      btnModalDirectDl.style.display = 'none';
      btnModalDirectDl.onclick = null;
    }
  }

  const btnModalTrailer = document.getElementById('btnModalTrailer');
  if (btnModalTrailer) {
    btnModalTrailer.onclick = (e) => {
      e?.stopPropagation?.();
      const cleanTitle = formatMovieCardTitle(details?.title || targetMovie.title);
      const yr = targetMovie.year || details?.year || extractMovieYear(targetMovie);
      const query = `${cleanTitle} ${yr || ''} official trailer`.trim();
      if (window.Capacitor?.Plugins?.NativePlayer?.openYouTube) {
        window.Capacitor.Plugins.NativePlayer.openYouTube({ query });
        return;
      }
      const ytAppUrl = `vnd.youtube://results?search_query=${encodeURIComponent(query)}`;
      const ytWebUrl = `https://www.youtube.com/results?search_query=${encodeURIComponent(query)}`;
      try {
        window.location.href = ytAppUrl;
        setTimeout(() => { window.open(ytWebUrl, '_system'); }, 700);
      } catch (_) {
        window.open(ytWebUrl, '_system');
      }
    };
  }
}

/**
 * Resolves final streaming/download URL via disk cache, pattern check, or Kotlin Native extractor
 */
async function resolveStreamHelper(targetUrl, allQuals = []) {
  if (!targetUrl) return { streamUrl: '', downloadUrl: '', type: 'video' };

  // ⚡ 0a. Instant Direct Bypass for Cinejoy, VidSrc2, VidLink, MultiEmbed & Universal Embed Streams (Zero 11s Timeout!)
  if (targetUrl.includes('cinejoy.to') || targetUrl.includes('vidsrc2.ru') || targetUrl.includes('vidlink.pro') || targetUrl.includes('multiembed.mov') || targetUrl.includes('vidsrc') || targetUrl.includes('2embed') || targetUrl.includes('autoembed')) {
    return { streamUrl: targetUrl, downloadUrl: targetUrl, embedUrl: targetUrl, type: 'embed' };
  }

  // ⚡ 0b. Instant Direct Preview Resolution for Google Drive
  if (targetUrl.includes('drive.google.com') || targetUrl.includes('drive.usercontent.google.com')) {
    const previewUrl = getGoogleDrivePreviewUrl(targetUrl);
    const gDriveObj = {
      streamUrl: previewUrl,
      downloadUrl: getGoogleDriveDownloadUrl(targetUrl),
      embedUrl: previewUrl,
      type: 'embed'
    };
    window.setStreamDiskCache?.(targetUrl, gDriveObj);
    return gDriveObj;
  }

  // ⚡ 0. Check Phone Storage Disk Cache (0 MB RAM, 0.001s Instant Play)
  const cached = window.getStreamDiskCache?.(targetUrl);
  if (cached && (cached.streamUrl || cached.downloadUrl)) {
    console.log('[Disk Cache Hit - 0.001s Play]', targetUrl, cached.streamUrl);
    return cached;
  }

  // ⚡ 1. Direct stream pattern check
  if (targetUrl.includes('.m3u8') ||
    targetUrl.includes('pixeldrain.com/api/file/') ||
    targetUrl.includes('pixeldrain.com/u/') ||
    targetUrl.includes('dlserver') ||
    targetUrl.includes('workers.dev') ||
    (targetUrl.includes('cinerustreams.com') && targetUrl.includes('.m3u8')) ||
    targetUrl.includes('.mp4') ||
    targetUrl.includes('.mkv')) {
    const playUrl = getPixelDrainApiUrl(targetUrl);
    const dlUrl = targetUrl.includes('pixeldrain') ? getPixelDrainDownloadUrl(targetUrl) : playUrl;
    const directObj = { streamUrl: playUrl, downloadUrl: dlUrl, type: 'video' };
    window.setStreamDiskCache?.(targetUrl, directObj);
    return directObj;
  }

  // 1b. Cineru Web Embed Player
  if (targetUrl.includes('cinerustreams.com')) {
    const cineruObj = { streamUrl: targetUrl, downloadUrl: targetUrl, embedUrl: targetUrl, type: 'embed' };
    window.setStreamDiskCache?.(targetUrl, cineruObj);
    return cineruObj;
  }

  const isUsersDrive = targetUrl.includes('usersdrive.com') || targetUrl.includes('userdrive.org');
  const nativeTimeout = isUsersDrive ? 25000 : 6000;

  if (isUsersDrive) {
    window.showToast?.('Finding video...', 'info');
  }

  // ⚡ 2. Android Native Kotlin Resolver (25.0s for UsersDrive Turnstile, 6.0s default)
  if (window.Capacitor?.Plugins?.NativeExtractor?.resolveMovieStream) {
    try {
      const timeoutPromise = new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), nativeTimeout));
      const nRes = await Promise.race([
        window.Capacitor.Plugins.NativeExtractor.resolveMovieStream({ url: targetUrl }),
        timeoutPromise
      ]);
      if (nRes && (nRes.streamUrl || nRes.downloadUrl)) {
        const sUrl = nRes.streamUrl || nRes.downloadUrl || '';
        const isValidDirect = !sUrl.includes('/links/') && (!isUsersDrive || sUrl.includes('userdrive.org') || sUrl.includes('/d/'));
        if (sUrl && isValidDirect) {
          if (isUsersDrive) {
            window.showToast?.('Video link found', 'info');
            window.showToast?.('Ready to play', 'success');
          }
          const resolvedObj = {
            streamUrl: sUrl,
            downloadUrl: nRes.downloadUrl || sUrl,
            embedUrl: nRes.embedUrl || '',
            type: nRes.type || (nRes.isZip ? 'download' : 'video'),
            isZip: nRes.isZip || false,
            filename: nRes.fileName || nRes.filename || '',
            switchedQuality: nRes.switchedQuality || ''
          };
          window.setStreamDiskCache?.(targetUrl, resolvedObj);
          return resolvedObj;
        }
      }
    } catch (_) { }
  }

  // ⚡ 3. On-Device Standalone JavaScript Engine fallback (skip for UsersDrive)
  if (!isUsersDrive && window.StandaloneEngine?.resolveFinalDownloadUrl) {
    try {
      const timeoutPromise = new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), 5000));
      const sRes = await Promise.race([
        window.StandaloneEngine.resolveFinalDownloadUrl(targetUrl, []),
        timeoutPromise
      ]);
      if (sRes && (sRes.streamUrl || sRes.downloadUrl)) {
        const sUrl = sRes.streamUrl || sRes.downloadUrl || '';
        if (sUrl && !sUrl.includes('/links/')) {
          window.setStreamDiskCache?.(targetUrl, sRes);
          return sRes;
        }
      }
    } catch (_) { }
  }

  if (isUsersDrive) {
    window.showToast?.('Download link could not be found', 'error');
    return { streamUrl: '', downloadUrl: targetUrl, type: 'error', error: 'Download link could not be found' };
  }

  const isEmbedLink = targetUrl.includes('embed') || targetUrl.includes('drive.google') || targetUrl.includes('mega.nz') || targetUrl.includes('filespayout') || targetUrl.includes('/links/') || targetUrl.includes('cinejoy.to') || targetUrl.includes('vidsrc2') || targetUrl.includes('vidsrc') || targetUrl.includes('vidlink') || targetUrl.includes('multiembed');
  return { streamUrl: targetUrl, downloadUrl: targetUrl, type: isEmbedLink ? 'embed' : 'video' };
}


// ============================================================================
// SECTION 10: Movie Details UI & Quality Card Handlers (Play / Leech / DL)
// ============================================================================

/**
 * Renders movie synopsis, backdrop, badges, and interactive quality options
 */
function renderDetailsUi(details, movie) {
  if (!details) return;

  if (!movieModal || movieModal.classList.contains('hidden')) {
    console.log('[movies.js] renderDetailsUi skipped: movieModal is currently closed/hidden');
    return;
  }

  const activeKey = currentActiveMovieUrl || window.state?.selectedMovie?.link || window.state?.selectedMovie?.id || window.state?.selectedMovie?.title;
  const incomingKey = movie?.link || movie?.id || movie?.title;
  if (activeKey && incomingKey && activeKey !== incomingKey) {
    console.warn('[movies.js] Stale renderDetailsUi blocked! Active movie:', activeKey, '!= Incoming details for:', incomingKey);
    return;
  }

  const targetMovie = movie || window.state.selectedMovie || {};
  window.currentMovieDetails = details;
  window.currentMovieObj = targetMovie;

  const exactCardPoster = targetMovie.poster || targetMovie.thumbnail || (details.poster && !details.poster.includes('unsplash') ? details.poster : '');
  if (exactCardPoster) {
    targetMovie.poster = exactCardPoster;
    targetMovie.thumbnail = exactCardPoster;
    if (modalMoviePoster) {
      modalMoviePoster.setAttribute('referrerpolicy', 'no-referrer');
      modalMoviePoster.src = exactCardPoster;
    }
    if (modalBackdropImg) {
      modalBackdropImg.setAttribute('referrerpolicy', 'no-referrer');
      modalBackdropImg.src = exactCardPoster;
    }
    if (targetMovie.link) {
      const gridCardImg = document.querySelector(`.movie-card[data-link="${targetMovie.link}"] .poster-wrapper img`);
      if (gridCardImg && (!gridCardImg.src || gridCardImg.src.startsWith('data:image') || gridCardImg.src.includes('unsplash'))) {
        gridCardImg.src = exactCardPoster;
      }
    }
  }

  const cleanTitle = formatMovieCardTitle(details.title || targetMovie.title);
  if (cleanTitle && modalMovieTitle) {
    modalMovieTitle.textContent = cleanTitle;
  }
  if (modalSourceBadge) {
    let rawSrc = targetMovie.source || details.source || 'Sinhalasub';
    if (rawSrc.toLowerCase().includes('cinejoy') || rawSrc.toLowerCase().includes('netflix') || targetMovie.isNetflix || (targetMovie.link || '').includes('cinejoy.to')) {
      rawSrc = 'Netflix';
    }
    modalSourceBadge.textContent = rawSrc;
    modalSourceBadge.className = `modal-source-badge ${getSourceBadgeClass(rawSrc)}`;
  }
  if (modalMovieRating) {
    const verifiedScore = formatRatingNumber(details.rating || targetMovie.rating, details.title || targetMovie.title);
    modalMovieRating.innerHTML = `<i class="fa-solid fa-star" style="color: #f5c518;"></i> ${verifiedScore} <span style="font-size: 0.8em; opacity: 0.85; margin-left: 2px;">IMDb</span>`;
  }

  setupModalWatchlistAndTrailer(targetMovie, details);

  const synopsisText = details.synopsis || targetMovie.title || 'High-speed cloud stream available.';
  if (modalMovieSynopsis) {
    modalMovieSynopsis.textContent = synopsisText;
  }

  if (btnToggleSynopsis && synopsisText.length > 130) {
    btnToggleSynopsis.classList.remove('hidden');
    btnToggleSynopsis.onclick = () => {
      const isExpanded = modalMovieSynopsis.classList.toggle('expanded');
      btnToggleSynopsis.classList.toggle('expanded', isExpanded);
      btnToggleSynopsis.innerHTML = isExpanded
        ? `<span>Read Less</span> <i class="fa-solid fa-chevron-up"></i>`
        : `<span>Read More</span> <i class="fa-solid fa-chevron-down"></i>`;
    };
  } else if (btnToggleSynopsis) {
    btnToggleSynopsis.classList.add('hidden');
  }

  // 🌐 Cross-Portal Isolation Switcher: Show other websites that have this exact title
  renderOtherPortalsChips(targetMovie);

  const movieSrc = (targetMovie?.source || details?.source || '').toLowerCase();
  const movieLnk = (targetMovie?.link || details?.link || '').toLowerCase();

  const isSinhalasub = movieSrc.includes('sinhalasub') || movieLnk.includes('sinhalasub') || (!movieSrc.includes('netflix') && !movieSrc.includes('pirate') && !movieSrc.includes('yts') && !movieSrc.includes('baiscope') && !movieSrc.includes('sub.lk') && !movieSrc.includes('sublk') && !movieLnk.includes('netflix') && !movieLnk.includes('piratelk') && !movieLnk.includes('yts') && !movieLnk.includes('baiscope') && !/(?:^|[^a-z0-9])sub\.lk/i.test(movieLnk));
  const isPirateLk = !isSinhalasub && (movieSrc.includes('pirate') || movieLnk.includes('piratelk'));
  const isBaiscope = !isSinhalasub && (movieSrc.includes('baiscope') || movieLnk.includes('baiscope'));
  const isYts = !isSinhalasub && (movieSrc.includes('yts') || movieLnk.includes('yts.') || movieLnk.includes('yts.mx'));
  const isNetflix = !isSinhalasub && !!(
    targetMovie?.isNetflix ||
    details?.isNetflix ||
    movieSrc.includes('netflix') ||
    movieLnk.includes('netflix.com') ||
    movieLnk.includes('cinejoy.to')
  );
  const isSubLk = !isSinhalasub && (movieSrc.includes('sub.lk') || movieSrc.includes('sublk') || /(?:^|[^a-z0-9])sub\.lk/i.test(movieLnk));

  // Guarantee Server 1: VidSrc2 Pro & Server 2: Cinejoy VIP are available for Netflix and YTS releases
  if (isNetflix || isYts) {
    const hasVidSrc2 = (details.qualities || []).some(q => (q.downloadUrl || q.streamUrl || '').includes('vidsrc2.ru') || (q.serverName || '').toLowerCase().includes('vidsrc2'));
    const hasCinejoy = (details.qualities || []).some(q => (q.downloadUrl || q.streamUrl || '').includes('cinejoy.to') || (q.serverName || '').toLowerCase().includes('cinejoy'));

    if (!hasVidSrc2 || !hasCinejoy) {
      const id = targetMovie.tmdb || details.tmdb || targetMovie.imdb || details.imdb;
      const cleanT = formatMovieCardTitle(targetMovie.title || details.title);
      const code = id || encodeURIComponent(cleanT);

      const sVidSrc2 = `https://vidsrc2.ru/embed/movie/${code}`;
      const sCinejoy = id ? `https://cinejoy.to/watch/movie/${id}` : `https://cinejoy.to/search`;

      const vipServers = [];
      if (!hasVidSrc2) {
        vipServers.push({
          quality: '🌟 Server 1: VidSrc2 Pro – 1080p Full HD • Multi-Audio',
          provider: 'VidSrc2 Pro',
          downloadUrl: sVidSrc2,
          streamUrl: sVidSrc2,
          size: '1080p Full HD',
          type: 'embed',
          serverName: 'VidSrc2 Pro'
        });
      }
      if (!hasCinejoy) {
        vipServers.push({
          quality: '🚀 Server 2: Cinejoy VIP – 4K UHD / 1080p Stream',
          provider: 'Cinejoy VIP',
          downloadUrl: sCinejoy,
          streamUrl: sCinejoy,
          size: '4K / 1080p Ultra HD',
          type: 'embed',
          serverName: 'Cinejoy VIP'
        });
      }
      details.qualities = [...vipServers, ...(details.qualities || [])];
    }
  }

  // 🛡️ Strict Portal Link Isolation: Show ONLY links belonging to this specific portal
  if (details.qualities && details.qualities.length > 0) {
    details.qualities = details.qualities.filter(q => {
      if (!q) return false;
      const u = (q.downloadUrl || q.streamUrl || q.url || '').toLowerCase();
      const p = (q.provider || '').toLowerCase();
      const s = (q.serverName || '').toLowerCase();
      const qual = (q.quality || '').toLowerCase();
      const combo = `${u} ${p} ${s} ${qual}`;

      // Global ban: FilesPayouts is strictly prohibited across all tabs
      if (combo.includes('filespayout')) return false;

      // Ban unwanted third-party ad players
      if (combo.includes('multiembed') || combo.includes('vidlink.pro')) return false;

      if (isSinhalasub) {
        // Sinhalasub: ALLOW Sinhalasub DLServer, PixelDrain, GDrive, GoFile, AkiraBox
        // Disallow foreign servers: VidSrc2, Cinejoy, PirateLK, Sub.lk, Baiscope
        if (combo.includes('vidsrc2') || combo.includes('cinejoy') || s.includes('vidsrc2') || s.includes('cinejoy')) return false;
        if (combo.includes('piratelk') || p.includes('piratelk')) return false;
        if (combo.includes('baiscope') || combo.includes('baiscopeslk')) return false;
        if (p.includes('cineru') || /(?:^|[^a-z0-9])dl\.sub\.lk/i.test(u) || (/(?:^|[^a-z0-9])sub\.lk/i.test(combo) && !combo.includes('sinhalasub'))) return false;
        return true;
      }

      if (isPirateLk) {
        // PirateLK: MUST NOT have Sinhalasub, DLServer, Sub.lk, Baiscope, VidSrc2, Cinejoy
        if (combo.includes('sinhalasub') || combo.includes('dlserver-01') || combo.includes('dlserver-02') || p.includes('sinhalasub')) return false;
        if (combo.includes('vidsrc2') || combo.includes('cinejoy') || s.includes('vidsrc2') || s.includes('cinejoy')) return false;
        if (combo.includes('baiscope') || combo.includes('baiscopeslk')) return false;
        if (p.includes('cineru') || (/(?:^|[^a-z0-9])sub\.lk/i.test(combo) && !combo.includes('sinhalasub'))) return false;
        return true;
      }

      if (isSubLk) {
        // Sub.lk: MUST NOT have Sinhalasub, DLServer, PirateLK, Baiscope, VidSrc2, Cinejoy
        if (combo.includes('sinhalasub') || combo.includes('dlserver-01') || combo.includes('dlserver-02') || p.includes('sinhalasub')) return false;
        if (combo.includes('vidsrc2') || combo.includes('cinejoy') || s.includes('vidsrc2') || s.includes('cinejoy')) return false;
        if (combo.includes('piratelk') || p.includes('piratelk')) return false;
        if (combo.includes('baiscope') || combo.includes('baiscopeslk')) return false;
        return true;
      }

      if (isBaiscope) {
        // Baiscope: MUST NOT have Sinhalasub, DLServer, PirateLK, Sub.lk, VidSrc2, Cinejoy
        if (combo.includes('sinhalasub') || combo.includes('dlserver-01') || combo.includes('dlserver-02') || p.includes('sinhalasub')) return false;
        if (combo.includes('vidsrc2') || combo.includes('cinejoy') || s.includes('vidsrc2') || s.includes('cinejoy')) return false;
        if (combo.includes('piratelk') || p.includes('piratelk')) return false;
        if (p.includes('cineru') || (/(?:^|[^a-z0-9])sub\.lk/i.test(combo) && !combo.includes('sinhalasub'))) return false;
        return true;
      }

      if (isYts) {
        // YTS: Allow VidSrc2 Pro, Cinejoy VIP and YTS Torrents/Magnets. Exclude other portal scrapers
        if (combo.includes('sinhalasub') || combo.includes('dlserver')) return false;
        if (combo.includes('piratelk') || combo.includes('baiscope')) return false;
        if (/(?:^|[^a-z0-9])sub\.lk/i.test(combo) && !combo.includes('sinhalasub')) return false;
        return true;
      }

      return true;
    });
  }

  // Render interactive quality rows
  renderQualitiesListContent(details.qualities || [], details, targetMovie);

  // Setup Season & Episode Selector if this is a TV series
  setupSeriesEpisodes(targetMovie, details);
}

/**
 * Renders interactive switcher chips if this movie is also hosted on any of the other 5 portals
 */
function renderOtherPortalsChips(targetMovie) {
  let container = document.getElementById('modalOtherPortalsContainer');
  if (!container) {
    const qSec = document.querySelector('.qualities-section');
    const qList = document.getElementById('modalQualitiesList');
    if (qList && qSec) {
      container = document.createElement('div');
      container.id = 'modalOtherPortalsContainer';
      container.className = 'other-portals-container';
      qSec.insertBefore(container, qList);
    } else {
      return;
    }
  }
  container.innerHTML = '';
  if (!targetMovie || !targetMovie.title || (currentPortalFilter && currentPortalFilter !== 'all')) {
    container.style.display = 'none';
    return;
  }

  const rawTargetTitle = (targetMovie.title || '').toLowerCase();
  const normTitle = rawTargetTitle
    .replace(/\b(sinhala\s*sub|sinhala\s*subtitles?|sinhala|sub|subtitles?|web-?dl|bluray|hdrip|1080p|720p|480p)\b/gi, '')
    .replace(/[^a-z0-9]/g, '')
    .trim();
  const targetYear = (extractMovieYear(targetMovie) || '').trim();
  const targetSrc = (targetMovie.source || (targetMovie.isNetflix ? 'netflix' : 'sinhalasub')).toLowerCase();

  const allCandidates = (portalMovieStorage.all && portalMovieStorage.all.length > 0)
    ? portalMovieStorage.all
    : (rawMovieResults || []);

  const matchingOtherPortals = [];
  const seenPortals = new Set();

  const getPortalKey = (src, lnk) => {
    const s = (src || '').toLowerCase();
    const l = (lnk || '').toLowerCase();
    if (s.includes('sinhalasub') || l.includes('sinhalasub')) return 'sinhalasub';
    if (s.includes('netflix') || l.includes('netflix.com') || l.includes('cinejoy.to')) return 'netflix';
    if (s.includes('pirate') || l.includes('piratelk')) return 'piratelk';
    if (s.includes('yts') || l.includes('yts.')) return 'yts';
    if (s.includes('baiscope') || l.includes('baiscope')) return 'baiscope';
    if (s.includes('sub.lk') || s.includes('sublk') || /(?:^|[^a-z0-9])sub\.lk/i.test(l)) return 'sublk';
    return 'sinhalasub';
  };

  const currentPortalKey = getPortalKey(targetSrc, targetMovie.link);

  for (const m of allCandidates) {
    if (!m || !m.title || m === targetMovie || m.link === targetMovie.link) continue;
    const mPortalKey = getPortalKey(m.source, m.link);
    if (mPortalKey === currentPortalKey) continue;

    const mNorm = (m.title || '').toLowerCase()
      .replace(/\b(sinhala\s*sub|sinhala\s*subtitles?|sinhala|sub|subtitles?|web-?dl|bluray|hdrip|1080p|720p|480p)\b/gi, '')
      .replace(/[^a-z0-9]/g, '')
      .trim();

    const isMatch = (normTitle && mNorm && (normTitle === mNorm || (normTitle.length > 5 && (mNorm.includes(normTitle) || normTitle.includes(mNorm)))));
    if (!isMatch) continue;

    const mYear = (extractMovieYear(m) || '').trim();
    if (targetYear && mYear && Math.abs(parseInt(targetYear, 10) - parseInt(mYear, 10)) > 1) {
      continue;
    }

    if (!seenPortals.has(mPortalKey)) {
      seenPortals.add(mPortalKey);
      matchingOtherPortals.push({ movie: m, portalKey: mPortalKey });
    }
  }

  if (matchingOtherPortals.length === 0) {
    container.style.display = 'none';
    return;
  }

  const portalConfig = {
    sinhalasub: { label: 'Sinhalasub', icon: 'fa-closed-captioning', color: '#00f2fe', bg: 'rgba(0, 242, 254, 0.12)', border: 'rgba(0, 242, 254, 0.35)' },
    sublk: { label: 'Sub.lk', icon: 'fa-closed-captioning', color: '#ff4757', bg: 'rgba(255, 71, 87, 0.12)', border: 'rgba(255, 71, 87, 0.35)' },
    baiscope: { label: 'Baiscope', icon: 'fa-closed-captioning', color: '#2ed573', bg: 'rgba(46, 213, 115, 0.12)', border: 'rgba(46, 213, 115, 0.35)' },
    piratelk: { label: 'PirateLK', icon: 'fa-skull-crossbones', color: '#a855f7', bg: 'rgba(168, 85, 247, 0.12)', border: 'rgba(168, 85, 247, 0.35)' },
    yts: { label: 'YTS 4K', icon: 'fa-compact-disc', color: '#eab308', bg: 'rgba(234, 179, 8, 0.12)', border: 'rgba(234, 179, 8, 0.35)' },
    netflix: { label: 'Netflix', icon: 'fa-n', color: '#ff3848', bg: 'rgba(229, 9, 20, 0.15)', border: 'rgba(229, 9, 20, 0.4)' }
  };

  container.style.display = 'block';
  container.innerHTML = `
    <div class="other-portals-card" style="margin: 6px 0 16px 0; padding: 12px 14px; background: rgba(15, 23, 42, 0.75); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 12px; backdrop-filter: blur(8px);">
      <div style="font-size: 0.78rem; font-weight: 700; color: #cbd5e1; margin-bottom: 9px; display: flex; align-items: center; justify-content: space-between;">
        <span style="display: flex; align-items: center; gap: 6px;">
          <i class="fa-solid fa-arrows-split-up-and-left" style="color: var(--accent-cyan);"></i>
          <span>Also Available On Other Web Portals</span>
        </span>
        <span style="font-size: 0.72rem; color: var(--text-muted); font-weight: 500;">${matchingOtherPortals.length} other source${matchingOtherPortals.length > 1 ? 's' : ''}</span>
      </div>
      <div class="other-portals-chips" style="display: flex; gap: 8px; flex-wrap: wrap;"></div>
    </div>
  `;

  const chipsContainer = container.querySelector('.other-portals-chips');
  matchingOtherPortals.forEach(item => {
    const cfg = portalConfig[item.portalKey] || { label: item.movie.source || 'Website', icon: 'fa-film', color: '#00f2fe', bg: 'rgba(255,255,255,0.08)', border: 'rgba(255,255,255,0.2)' };
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'other-portal-btn';
    btn.style.cssText = `padding: 6px 12px; border-radius: 8px; font-size: 0.78rem; font-weight: 700; cursor: pointer; display: inline-flex; align-items: center; gap: 6px; border: 1px solid ${cfg.border}; background: ${cfg.bg}; color: ${cfg.color}; transition: transform 0.15s ease, background 0.15s ease;`;
    btn.innerHTML = `<i class="fa-solid ${cfg.icon}"></i> <span>${cfg.label}</span> <i class="fa-solid fa-arrow-right" style="font-size: 0.68rem; opacity: 0.7;"></i>`;
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      window.openMovieModal(item.movie);
    });
    chipsContainer.appendChild(btn);
  });
}

/**
 * Standalone Qualities List Renderer (can be called dynamically when switching episodes)
 */
function renderQualitiesListContent(qualitiesInput, details, targetMovie) {
  if (!modalQualitiesList) return;
  modalQualitiesList.innerHTML = '';

  const cleanMovieTitle = formatMovieCardTitle(targetMovie?.title || details?.title || '');
  const seenUrls = new Set();
  const seenFingerprints = new Set();
  const rawList = (qualitiesInput || []).map(q => {
    if (!q) return null;
    const dl = q.downloadUrl || q.streamUrl || q.url || '';
    return {
      ...q,
      downloadUrl: dl,
      streamUrl: q.streamUrl || dl,
      url: q.url || dl
    };
  }).filter(q => q && q.downloadUrl && !isBadStreamLink(q.downloadUrl, (q.provider || '') + ' ' + (q.quality || '')));

  const qualities = [];
  rawList.forEach(q => {
    const qUrl = (q.downloadUrl || q.streamUrl || '').toLowerCase();
    const qServer = (q.serverName || '').toLowerCase();
    const qQual = (q.quality || '').toLowerCase();
    const qProv = (q.provider || '').toLowerCase();

    // Drop unnecessary & ad-heavy players unconditionally
    if (qServer.includes('multiembed') || qUrl.includes('multiembed') || qQual.includes('multiembed') || qProv.includes('multiembed')
      || qServer.includes('vidlink') || qUrl.includes('vidlink') || qQual.includes('vidlink') || qProv.includes('vidlink')
      || qServer.includes('filespayout') || qUrl.includes('filespayout') || qQual.includes('filespayout') || qProv.includes('filespayout')) {
      return;
    }

    const isVipServer = qServer === 'cinejoy vip' || qServer === 'vidsrc2 pro' || qServer === 'cinejoy direct'
      || qUrl.includes('cinejoy.to') || qUrl.includes('vidsrc2.ru')
      || qQual.includes('cinejoy vip') || qQual.includes('vidsrc2 pro') || qQual.includes('cinejoy direct')
      || qProv.includes('cinejoy direct');
    if (isVipServer) {
      let vipKey = '';
      if (qServer.includes('vidsrc2') || qUrl.includes('vidsrc2.ru') || qQual.includes('vidsrc2')) vipKey = 'vip_vidsrc2';
      else if (qServer.includes('cinejoy direct') || qQual.includes('cinejoy direct') || qProv.includes('cinejoy direct')) vipKey = 'vip_cinejoy_direct';
      else if (qServer.includes('cinejoy') || qUrl.includes('cinejoy.to') || qQual.includes('cinejoy')) vipKey = 'vip_cinejoy';
      else vipKey = 'vip_' + (qServer || qQual);

      if (vipKey && seenUrls.has(vipKey)) {
        return; // Skip duplicate VIP server card
      }
      seenUrls.add(vipKey);
      qualities.push(q);
      return;
    }

    const rawUrl = q.downloadUrl || '';
    const cleanUrl = rawUrl.startsWith('magnet:')
      ? rawUrl.trim()
      : ((rawUrl.includes('token=') || rawUrl.includes('id=')) ? rawUrl.trim() : rawUrl.split('?')[0].replace(/\/+$/, '').toLowerCase());
    const provKey = (q.provider || '').toLowerCase().replace(/[^a-z0-9]/g, '');
    const qualKey = (q.quality || '').toLowerCase().replace(/[^a-z0-9]/g, '');
    const fingerprint = `${provKey}-${qualKey}-${cleanUrl.slice(-15)}`;

    if (cleanUrl && !seenUrls.has(cleanUrl) && !seenFingerprints.has(fingerprint)) {
      seenUrls.add(cleanUrl);
      seenFingerprints.add(fingerprint);
      qualities.push(q);
    }
  });

  if (qualities.length > 0) {
    qualities.forEach(q => {
      const item = document.createElement('div');

      let badgeClass = 'badge-720p';
      let qualIcon = 'fa-film';
      let resLabel = q.quality || 'HD 720p';

      if (q.quality.includes('4K') || q.quality.includes('2160')) {
        badgeClass = 'badge-4k';
        qualIcon = 'fa-crown';
        resLabel = '4K UHD';
      } else if (q.quality.includes('FHD') || q.quality.includes('1080')) {
        badgeClass = 'badge-1080p';
        qualIcon = 'fa-tv';
        resLabel = '1080p FHD';
      } else if (q.quality.includes('480') || q.quality.includes('SD')) {
        badgeClass = 'badge-480p';
        qualIcon = 'fa-compact-disc';
        resLabel = '480p SD';
      } else {
        badgeClass = 'badge-720p';
        qualIcon = 'fa-film';
        resLabel = '720p HD';
      }

      item.className = `quality-card-row ${badgeClass}-accent`;

      // High-Performance Provider Meta Lookup
      const pMeta = getProviderDisplayMeta(q.provider, q.downloadUrl, q.label);
      const speedChipHtml = `<span class="q-chip speed" style="color: ${pMeta.color}; background: ${pMeta.color}18; border-color: ${pMeta.color}4d;"><i class="${pMeta.icon.includes('fa-') ? (pMeta.icon.includes('brands') ? pMeta.icon : 'fa-solid ' + pMeta.icon) : 'fa-solid fa-server'}"></i> ${pMeta.label}</span>`;
      const sizeLabel = (q.size && q.size !== '----' && q.size !== 'Cloud File' && q.size !== 'Auto Stream') ? q.size : '1.4 GB';

      const isQualityZip = q.isZip ||
        (q.quality?.toLowerCase() || '').includes('.zip') ||
        (q.quality?.toLowerCase() || '').includes('zip package') ||
        (q.downloadUrl?.toLowerCase() || '').includes('.zip') ||
        (q.provider?.toLowerCase() || '').includes('zip') ||
        q.type === 'download';
      const zipChipHtml = isQualityZip
        ? `<span class="q-chip zip" style="color: #f59e0b; background: rgba(245,158,11,0.14); border-color: rgba(245,158,11,0.35);"><i class="fa-solid fa-file-zipper"></i> ZIP Package</span>`
        : '';

      const qUrl = (q.downloadUrl || q.streamUrl || '').toLowerCase();
      const qServer = (q.serverName || '').toLowerCase();
      const qQual = (q.quality || '').toLowerCase();
      const isVipCard = qServer === 'cinejoy vip' || qServer === 'vidsrc2 pro' || qServer === 'cinejoy direct'
        || qUrl.includes('cinejoy.to') || qUrl.includes('vidsrc2.ru')
        || qQual.includes('cinejoy vip') || qQual.includes('vidsrc2 pro') || qQual.includes('cinejoy direct');

      const isFileHostDownload = !isVipCard && !isQualityZip && (
        qUrl.includes('usersdrive') || qUrl.includes('userdrive') ||
        qUrl.includes('mediafire') ||
        qUrl.includes('1fichier') || (pMeta.label || '').toLowerCase().includes('usersdrive')
      );

      const serverTitle = q.serverTitle || (isVipCard ? q.quality : '');
      const serverTitleHtml = serverTitle ? `
        <div class="quality-server-title-bar" style="display: flex; align-items: center; gap: 8px; padding: 7px 12px; background: linear-gradient(135deg, rgba(15, 23, 42, 0.92) 0%, rgba(30, 41, 59, 0.85) 100%); border: 1px solid rgba(0, 242, 254, 0.35); border-radius: 10px; margin: 4px 0 2px 0;">
          <span style="font-size: 0.86rem; font-weight: 700; color: #38bdf8; line-height: 1.35; letter-spacing: 0.2px;">${serverTitle}</span>
        </div>
      ` : '';

      const subChipHtml = isVipCard
        ? `<span class="q-chip sub" style="color: #00f2fe; background: rgba(0,242,254,0.12); border-color: rgba(0,242,254,0.3);"><i class="fa-solid fa-globe"></i> Multi-Audio & Sub</span>`
        : `<span class="q-chip sub" style="color: #a55eea; background: rgba(165,94,234,0.12); border-color: rgba(165,94,234,0.3);"><i class="fa-solid fa-closed-captioning"></i> Sinhala Sub</span>`;

      let actionButtonsHtml = '';
      if (isQualityZip) {
        actionButtonsHtml = `
          <button class="btn-direct-dl" style="grid-column: span 2;" title="Direct Download ZIP Archive">
            <i class="fa-solid fa-file-zipper"></i>
            <span>Download ZIP Package</span>
          </button>
          <button class="btn-upload-drive" title="Upload to Google Drive with 0 MB PC/Mobile Data">
            <span class="btn-main-label"><i class="fa-solid fa-cloud-arrow-up"></i> Leech ZIP to Google Drive</span>
            <span class="btn-zero-tag"><i class="fa-solid fa-bolt"></i> 0 MB Data</span>
          </button>
        `;
      } else if (isVipCard) {
        // VIP Streaming Server: Hardware Pro Player, In-App Popup Player, Cinejoy Direct DL & Drive Leech!
        actionButtonsHtml = `
          <button class="btn-pro-player" title="Watch in Hardware-Accelerated Pro Cinema Player (ExoPlayer with Audio Tracks & Subtitles)">
            <i class="fa-solid fa-film"></i>
            <span>Watch Cinema (Pro Player)</span>
          </button>
          <button class="btn-popup-player" title="Watch in In-App Popup Player">
            <i class="fa-solid fa-window-restore"></i>
            <span>Popup Player</span>
          </button>
          <button class="btn-direct-dl" title="Cinejoy Direct Download (4K UHD / 1080p FHD)">
            <i class="fa-solid fa-download"></i>
            <span>Direct DL</span>
          </button>
          <button class="btn-upload-drive" title="Upload to Google Drive with 0 MB PC/Mobile Data">
            <span class="btn-main-label"><i class="fa-solid fa-cloud-arrow-up"></i> Leech to Google Drive</span>
            <span class="btn-zero-tag"><i class="fa-solid fa-bolt"></i> 0 MB Data</span>
          </button>
        `;
      } else if (isFileHostDownload) {
        // Filehost / Locker (UsersDrive, MEGA): Strictly high-speed download & 0 MB cloud leech options!
        actionButtonsHtml = `
          <button class="btn-direct-dl" style="grid-column: span 1;" title="Direct Download to Device via Cloud Browser">
            <i class="fa-solid fa-download"></i>
            <span>Direct DL</span>
          </button>
          <button class="btn-upload-drive" style="grid-column: span 1;" title="Upload to Google Drive with 0 MB PC/Mobile Data">
            <span class="btn-main-label"><i class="fa-solid fa-cloud-arrow-up"></i> Leech to Google Drive</span>
            <span class="btn-zero-tag"><i class="fa-solid fa-bolt"></i> 0 MB Data</span>
          </button>
        `;
      } else {
        // Direct media streams (PixelDrain, Direct MP4/MKV, YTS Torrents/Magnets): Full suite of streaming and downloading!
        actionButtonsHtml = `
          <button class="btn-pro-player" title="Watch in Hardware-Accelerated Pro Cinema Player (ExoPlayer with Audio Tracks & Subtitles)">
            <i class="fa-solid fa-film"></i>
            <span>Watch Cinema (Pro Player)</span>
          </button>
          <button class="btn-popup-player" title="Watch in In-App Popup Player">
            <i class="fa-solid fa-window-restore"></i>
            <span>Popup Player</span>
          </button>
          <button class="btn-direct-dl" title="Direct Binary Download to Device">
            <i class="fa-solid fa-download"></i>
            <span>Direct DL</span>
          </button>
          <button class="btn-upload-drive" title="Upload to Google Drive with 0 MB PC/Mobile Data">
            <span class="btn-main-label"><i class="fa-solid fa-cloud-arrow-up"></i> Leech to Google Drive</span>
            <span class="btn-zero-tag"><i class="fa-solid fa-bolt"></i> 0 MB Data</span>
          </button>
        `;
      }

      item.innerHTML = `
        <div class="quality-card-top-row">
          <div class="quality-badge ${badgeClass}">
            <i class="fa-solid ${isQualityZip ? 'fa-file-zipper' : qualIcon}"></i>
            <span>${resLabel}</span>
          </div>
          <div class="quality-chips-row">
            <span class="q-chip size" title="File Size"><i class="fa-solid fa-hard-drive"></i> ${sizeLabel}</span>
            ${subChipHtml}
            ${zipChipHtml}
            ${speedChipHtml}
          </div>
        </div>
        ${serverTitleHtml}
        <div class="quality-actions">
          ${actionButtonsHtml}
        </div>
      `;

      const btnPro = item.querySelector('.btn-pro-player');
      const btnPopup = item.querySelector('.btn-popup-player');
      const btnLeech = item.querySelector('.btn-upload-drive');
      const btnDl = item.querySelector('.btn-direct-dl');

      const allQuals = details?.qualities || [];
      const cleanTitle = (cleanMovieTitle || targetMovie?.title || details?.title || '').replace(/\[.*?\]/g, '').replace(/\(.*?\)/g, '').replace(/sinhala.*$/i, '').trim();

      const findBestDirectDl = () => {
        return allQuals.find(x => x.downloadUrl && (x.downloadUrl.includes('pixeldrain') || x.provider?.toLowerCase().includes('pixeldrain')))
          || allQuals.find(x => x.downloadUrl && (x.downloadUrl.includes('dlserver') || x.downloadUrl.includes('sinhalasub.net') || x.provider?.toLowerCase().includes('cdn') || x.provider?.toLowerCase().includes('dlserver')))
          || allQuals.find(x => x.downloadUrl && (x.downloadUrl.includes('workers.dev') || x.downloadUrl.includes('drive.google')))
          || allQuals.find(x => x.downloadUrl && (x.downloadUrl.includes('.mp4') || x.downloadUrl.includes('.mkv')))
          || allQuals.find(x => x.downloadUrl && !x.downloadUrl.includes('cinerustreams') && !x.downloadUrl.includes('vidsrc2') && !x.downloadUrl.includes('vidlink') && !x.downloadUrl.includes('vidsrc') && !x.downloadUrl.includes('usersdrive') && !x.downloadUrl.includes('filespayout'));
      };

      const resolveStreamForPlayback = async () => {
        let finalStream = q.streamUrl || q.downloadUrl;
        let streamType = q.type || 'video';
        let resolvedData = null;

        if (!finalStream.includes('vidsrc2') && !finalStream.includes('vidlink') && !finalStream.includes('multiembed') && !finalStream.includes('autoembed') && !finalStream.includes('vidsrc')) {
          resolvedData = await resolveStreamHelper(finalStream, allQuals);
          finalStream = resolvedData.streamUrl || resolvedData.downloadUrl || finalStream;
          if (resolvedData.type) streamType = resolvedData.type;
        }

        if (!finalStream || resolvedData?.type === 'error') {
          window.showToast('Stream link unavailable. Please choose another server or tap Direct DL to download.', 'warning');
          return null;
        }

        return { finalStream, streamType, resolvedData };
      };

      // Action 1A: Pro Cinema Player (ExoPlayer with hardware acceleration)
      if (btnPro) {
        btnPro.addEventListener('click', async () => {
          const origText = btnPro.innerHTML;
          btnPro.innerHTML = `<i class="fa-solid fa-circle-notch fa-spin"></i> Launching Pro Cinema...`;
          btnPro.disabled = true;
          window.showToast('🎬 Resolving High-Speed Pro Cinema Stream...', 'info');

          try {
            const imdbCode = details?.imdb || targetMovie?.imdb || (q.url?.match(/tt\d{7,8}/)?.[0]) || (q.streamUrl?.match(/tt\d{7,8}/)?.[0]) || (targetMovie?.link?.match(/tt\d{7,8}/)?.[0]) || '';

            if (q.type === 'torrent' || q.downloadUrl?.startsWith('magnet:') || q.downloadUrl?.includes('.torrent') || (q.provider || '').toLowerCase().includes('torrent')) {
              window.closeMovieModal();
              const targetMagnet = (q.streamUrl && q.streamUrl.startsWith('magnet:')) ? q.streamUrl : (q.downloadUrl && q.downloadUrl.startsWith('magnet:') ? q.downloadUrl : q.streamUrl || q.downloadUrl);

              if (window.Capacitor?.Plugins?.NativePlayer?.playTorrentStream) {
                window.showToast(`🍿 Opening Cinema Player for ${cleanTitle}...`, 'info');
                window.Capacitor.Plugins.NativePlayer.playTorrentStream({
                  streamUrl: targetMagnet,
                  title: `${cleanTitle} [${q.quality || 'Torrent Stream'}]`,
                  poster: targetMovie?.poster || details?.poster || ''
                }).catch(err => {
                  console.warn('[btnPro] playTorrentStream error:', err);
                  window.showToast('Torrent streaming error: ' + (err.message || err), 'error');
                });
                return;
              }

              window.openPlayer({
                title: `${cleanTitle} [${q.quality || 'Torrent Stream'}]`,
                streamUrl: targetMagnet,
                embedUrl: targetMagnet,
                downloadUrl: targetMagnet,
                type: 'torrent',
                isZip: false,
                allQualities: allQuals,
                imdb: imdbCode,
                isTv: details?.isTv || targetMovie?.isTv || false,
                poster: targetMovie?.poster || '',
                provider: q.provider || 'Torrent'
              });
              return;
            }

            const res = await resolveStreamForPlayback();
            if (!res || !res.finalStream) return;

            const serversList = (allQuals || []).map(item => ({
              quality: item.quality || '1080p',
              provider: item.provider || '',
              downloadUrl: item.downloadUrl || item.streamUrl || '',
              type: item.type || 'video'
            }));
            if (serversList.length === 0) {
              serversList.push({
                quality: q.quality || '1080p FHD',
                provider: q.provider || 'Universal Cinema Cloud',
                downloadUrl: res.finalStream,
                type: 'video'
              });
            }

            const isTvSeries = !!(details?.isTv || targetMovie?.isTv || currentSeriesData);
            const targetTmdb = details?.tmdb || targetMovie?.tmdb || (targetMovie?.id && String(targetMovie.id).startsWith('netflix_') ? String(targetMovie.id).replace('netflix_', '') : '') || '';
            const targetImdb = imdbCode || targetMovie?.imdb || details?.imdb || '';

            if (window.Capacitor?.Plugins?.NativePlayer?.playVideo) {
              window.closeMovieModal();
              await window.Capacitor.Plugins.NativePlayer.playVideo({
                url: res.finalStream,
                streamUrl: res.finalStream,
                title: `${cleanTitle} [${q.quality || 'FHD'}]`,
                poster: targetMovie?.poster || details?.poster || '',
                category: 'movies',
                serversJson: JSON.stringify(serversList),
                servers: serversList,
                isTv: isTvSeries,
                seriesTitle: targetMovie?.title || details?.title || cleanTitle,
                season: details?.activeSeason || currentSelectedSeason || 1,
                episode: details?.activeEpisode || currentSelectedEpisode || 1,
                tmdb: targetTmdb,
                imdb: targetImdb,
                seriesData: currentSeriesData || {},
                seriesJson: currentSeriesData ? JSON.stringify(currentSeriesData) : '{}'
              });
            } else {
              window.closeMovieModal();
              window.openPlayer({
                title: `${cleanTitle} [${q.quality || 'FHD'}]`,
                streamUrl: res.finalStream,
                embedUrl: res.finalStream,
                downloadUrl: q.downloadUrl || res.finalStream,
                driveId: q.driveId,
                isDrive: q.isDrive,
                type: res.streamType,
                isZip: isQualityZip,
                allQualities: allQuals,
                imdb: imdbCode,
                tmdb: details?.tmdb || targetMovie?.tmdb || (targetMovie?.id && String(targetMovie.id).startsWith('netflix_') ? String(targetMovie.id).replace('netflix_', '') : '') || '',
                season: details?.activeSeason || 1,
                episode: details?.activeEpisode || 1,
                isTv: details?.isTv || targetMovie?.isTv || false,
                poster: targetMovie?.poster || '',
                provider: q.provider || ''
              });
            }
          } catch (err) {
            console.warn('[btnPro] Cinema player launch error:', err);
            window.showToast('Cinema Player notice: ' + err.message, 'error');
          } finally {
            btnPro.innerHTML = origText;
            btnPro.disabled = false;
          }
        });
      }

      // Action 1B: Popup Player (In-app modal player)
      if (btnPopup) {
        btnPopup.addEventListener('click', async () => {
          const origText = btnPopup.innerHTML;
          btnPopup.innerHTML = `<i class="fa-solid fa-circle-notch fa-spin"></i> Loading...`;
          btnPopup.disabled = true;
          window.showToast('📱 Loading In-App Popup Player...', 'info');

          try {
            const imdbCode = details?.imdb || targetMovie?.imdb || (q.url?.match(/tt\d{7,8}/)?.[0]) || (q.streamUrl?.match(/tt\d{7,8}/)?.[0]) || (targetMovie?.link?.match(/tt\d{7,8}/)?.[0]) || '';

            if (q.type === 'torrent' || q.downloadUrl?.startsWith('magnet:') || q.downloadUrl?.includes('.torrent') || (q.provider || '').toLowerCase().includes('torrent')) {
              window.closeMovieModal();
              const targetMagnet = (q.streamUrl && q.streamUrl.startsWith('magnet:')) ? q.streamUrl : (q.downloadUrl && q.downloadUrl.startsWith('magnet:') ? q.downloadUrl : q.streamUrl || q.downloadUrl);
              window.openPlayer({
                title: `${cleanTitle} [${q.quality || 'Torrent Stream'}]`,
                streamUrl: targetMagnet,
                embedUrl: targetMagnet,
                downloadUrl: targetMagnet,
                type: 'torrent',
                isZip: false,
                allQualities: allQuals,
                imdb: imdbCode,
                tmdb: details?.tmdb || targetMovie?.tmdb || (targetMovie?.id && String(targetMovie.id).startsWith('netflix_') ? String(targetMovie.id).replace('netflix_', '') : '') || '',
                season: details?.activeSeason || 1,
                episode: details?.activeEpisode || 1,
                isTv: details?.isTv || targetMovie?.isTv || false,
                poster: targetMovie?.poster || '',
                provider: q.provider || 'Torrent'
              });
              return;
            }

            const res = await resolveStreamForPlayback();
            if (!res || !res.finalStream) return;

            window.closeMovieModal();
            window.openPlayer({
              title: `${cleanTitle} [${q.quality || 'FHD'}]`,
              streamUrl: res.finalStream,
              embedUrl: res.finalStream,
              downloadUrl: q.downloadUrl || res.finalStream,
              driveId: q.driveId,
              isDrive: q.isDrive,
              type: res.streamType,
              isZip: isQualityZip,
              allQualities: allQuals,
              imdb: imdbCode,
              tmdb: details?.tmdb || targetMovie?.tmdb || (targetMovie?.id && String(targetMovie.id).startsWith('netflix_') ? String(targetMovie.id).replace('netflix_', '') : '') || '',
              season: details?.activeSeason || 1,
              episode: details?.activeEpisode || 1,
              isTv: details?.isTv || targetMovie?.isTv || false,
              poster: targetMovie?.poster || '',
              provider: q.provider || ''
            });
          } catch (err) {
            console.warn('[btnPopup] Popup player error:', err);
            window.showToast('Failed to open player: ' + err.message, 'error');
          } finally {
            btnPopup.innerHTML = origText;
            btnPopup.disabled = false;
          }
        });
      }

      // Action 2: Leech to Cloud Drive
      if (btnLeech) {
        btnLeech.addEventListener('click', async () => {
          const origText = btnLeech.innerHTML;

          const qCardUrl = (q.downloadUrl || q.streamUrl || '').toLowerCase();
          const qCardServer = (q.serverName || '').toLowerCase();
          const qCardQual = (q.quality || '').toLowerCase();
          const qCardProv = (q.provider || '').toLowerCase();

          const isVipServer =
            qCardServer === 'cinejoy vip' || qCardServer === 'vidlink pro' || qCardServer === 'vidsrc2 pro' || qCardServer === 'multiembed vip' || qCardServer.includes('cinejoy') ||
            qCardUrl.includes('cinejoy.to') || qCardUrl.includes('vidlink.pro') || qCardUrl.includes('vidsrc2.ru') || qCardUrl.includes('multiembed.mov') ||
            qCardQual.includes('cinejoy vip') || qCardQual.includes('vidlink pro') || qCardQual.includes('vidsrc2 pro') || qCardQual.includes('multiembed vip') || qCardQual.includes('cinejoy') ||
            qCardProv.includes('cinejoy vip') || qCardProv.includes('vidlink vip') || qCardProv.includes('vidsrc2') || qCardProv.includes('multiembed') || qCardProv.includes('cinejoy');

          if (isVipServer) {
            // VIP servers: Open the Cinejoy Direct Download & Leech Options Picker Modal!
            window.openCinejoyDownloadPicker(targetMovie, details);
            return;
          }

          btnLeech.innerHTML = `<i class="fa-solid fa-circle-notch fa-spin"></i> Initializing Leech...`;
          btnLeech.disabled = true;

          try {
            let targetLeechUrl = q.downloadUrl || q.streamUrl;
            const isCaptchaHost = (targetLeechUrl.includes('filespayout') || targetLeechUrl.includes('/links/') || q.isEmbed) && !targetLeechUrl.includes('/d/');

            if (isCaptchaHost && details?.qualities && details.qualities.length > 1) {
              const directMirror = details.qualities.find(x =>
                x.downloadUrl &&
                (x.downloadUrl.includes('pixeldrain') || x.provider?.toLowerCase()?.includes('pixeldrain') ||
                  x.downloadUrl.includes('dlserver') || x.provider?.toLowerCase()?.includes('dlserver') ||
                  x.downloadUrl.includes('sinhalasub.net') || x.downloadUrl.includes('workers.dev') || x.downloadUrl.includes('drive.google'))
              );
              if (directMirror && (directMirror.downloadUrl || directMirror.streamUrl)) {
                targetLeechUrl = directMirror.downloadUrl || directMirror.streamUrl;
              }
            }

            const data = await resolveStreamHelper(targetLeechUrl, details?.qualities || []);
            const finalStream = data.downloadUrl || data.streamUrl || targetLeechUrl;

            if (window.startCloudTransfer) {
              window.startCloudTransfer({
                title: `${targetMovie?.title || cleanMovieTitle} [${data.switchedQuality || q.quality}]`,
                url: finalStream,
                type: 'movie',
                quality: data.switchedQuality || q.quality
              });
            }
            window.closeMovieModal();
          } catch (err) {
            window.showToast('Failed to resolve link: ' + err.message, 'error');
          } finally {
            btnLeech.innerHTML = origText;
            btnLeech.disabled = false;
          }
        });
      }

      // Action 3: Direct Binary Download
      if (btnDl) {
        btnDl.addEventListener('click', async (e) => {
          e.stopPropagation();

          // 1. Check if this card belongs strictly to the 3 VIP servers:
          //    - Server 1: VidSrc2 Pro (sVidSrc2)
          //    - Server 2: Cinejoy VIP (sCinejoy)
          //    - Server 3: MultiEmbed VIP (sMultiEmbed)
          const qCardUrl = (q.downloadUrl || q.streamUrl || '').toLowerCase();
          const qCardServer = (q.serverName || '').toLowerCase();
          const qCardQual = (q.quality || '').toLowerCase();
          const qCardProv = (q.provider || '').toLowerCase();

          const isVipServer =
            qCardServer === 'cinejoy vip' || qCardServer === 'vidlink pro' || qCardServer === 'vidsrc2 pro' || qCardServer === 'multiembed vip' || qCardServer.includes('cinejoy') ||
            qCardUrl.includes('cinejoy.to') || qCardUrl.includes('vidlink.pro') || qCardUrl.includes('vidsrc2.ru') || qCardUrl.includes('multiembed.mov') ||
            qCardQual.includes('cinejoy vip') || qCardQual.includes('vidlink pro') || qCardQual.includes('vidsrc2 pro') || qCardQual.includes('multiembed vip') || qCardQual.includes('cinejoy') ||
            qCardProv.includes('cinejoy vip') || qCardProv.includes('vidlink vip') || qCardProv.includes('vidsrc2') || qCardProv.includes('multiembed') || qCardProv.includes('cinejoy');

          if (isVipServer) {
            // ONLY for these VIP servers: Open the Cinejoy Direct Download Options Picker Modal!
            window.openCinejoyDownloadPicker(targetMovie, details);
            return;
          }

          // 2. For all other websites (SinhalaSub, Baiscope, PirateLK, etc.): keep exactly as they originally were!
          const origText = btnDl.innerHTML;
          btnDl.innerHTML = `<i class="fa-solid fa-circle-notch fa-spin"></i>`;
          btnDl.disabled = true;
          window.showToast('Resolving high-speed direct download...', 'info');

          try {
            let targetDlUrl = q.downloadUrl || q.streamUrl;
            const allQuals = details?.qualities || [];

            // If this quality is explicitly a standalone ZIP archive
            if (isQualityZip || q.ext === 'zip' || (targetDlUrl && targetDlUrl.endsWith('.zip'))) {
              let dlUrl = targetDlUrl;
              if (dlUrl.includes('pixeldrain.com')) dlUrl = getPixelDrainDownloadUrl(dlUrl);
              const safeTitle = (cleanMovieTitle || targetMovie?.title || details?.title || '').replace(/[/\\?%*:|"<>]/g, '_');
              const filename = `${safeTitle}_${q.quality || 'Archive'}.zip`;
              window.showToast('⚡ ZIP Archive detected. Starting Direct Download...', 'info');
              window.triggerDirectDownload(dlUrl, filename);
              return;
            }

            // If it's a torrent or magnet link
            if (targetDlUrl.startsWith('magnet:') || targetDlUrl.includes('.torrent') || targetDlUrl.includes('/torrent/download/')) {
              window.showToast('🚀 Direct Download active in Downloads Tab!', 'success');
              const filename = `${(targetMovie?.title || cleanMovieTitle).replace(/[/\\?%*:|"<>]/g, '_')}_${q.quality || '1080p'}`;
              window.triggerDirectDownload(targetDlUrl, filename);
              return;
            }

            if (targetDlUrl.includes('pixeldrain.com')) {
              targetDlUrl = getPixelDrainDownloadUrl(targetDlUrl);
            }

            const data = await resolveStreamHelper(targetDlUrl, allQuals);
            let finalDl = data.downloadUrl || data.streamUrl || targetDlUrl;

            if (finalDl.includes('pixeldrain.com')) {
              finalDl = getPixelDrainDownloadUrl(finalDl);
            }
            if (finalDl.includes('drive.google') || finalDl.includes('drive.usercontent.google')) {
              finalDl = getGoogleDriveDownloadUrl(finalDl);
            }

            if (finalDl.includes('mega.nz') || finalDl.includes('mega.co.nz') || finalDl.includes('mega.io')) {
              if (window.Capacitor?.Plugins?.NativeBrowser?.showBrowser) {
                window.showToast('⚡ Opening MEGA Cloud in browser to decrypt & download...', 'info');
                if (window.switchTab) window.switchTab('browser');
                window.Capacitor.Plugins.NativeBrowser.showBrowser({ url: finalDl }).catch(() => {
                  window.open(finalDl, '_system');
                });
              } else {
                window.showToast('⚡ Opening MEGA in browser...', 'info');
                window.open(finalDl, '_system');
              }
              return;
            }

            const isDirectLink = finalDl.includes('userdrive.org') || finalDl.includes('dl.usersdrive.com') || finalDl.includes('/d/') || finalDl.includes('workers.dev') || finalDl.includes('.mp4') || finalDl.includes('.mkv') || finalDl.includes('.zip');
            const isStillCaptchaPortal = (finalDl.includes('filespayout') || finalDl.includes('usersdrive')) && !isDirectLink;
            if (isStillCaptchaPortal) {
              if (window.Capacitor?.Plugins?.NativeBrowser?.showBrowser) {
                window.showToast('⚡ Opening UsersDrive in Cloud Browser to download...', 'info');
                if (window.switchTab) window.switchTab('browser');
                window.Capacitor.Plugins.NativeBrowser.showBrowser({ url: finalDl }).catch(() => {
                  window.open(finalDl, '_system');
                });
              } else {
                window.showToast('⚡ Opening Download portal in browser...', 'info');
                window.open(finalDl, '_system');
              }
              return;
            }

            let ext = isQualityZip || finalDl.includes('.zip') || (data.isZip) ? '.zip' : (finalDl.includes('.mkv') ? '.mkv' : '.mp4');
            const cleanQual = (q.quality || 'FHD')
              .replace(/^⚡\s*/, '')
              .replace(/[/\\?%*:|"<>]/g, '_')
              .trim();
            const safeTitle = (targetMovie?.title || cleanMovieTitle)
              .replace(/[/\\?%*:|"<>]/g, '_')
              .trim();
            const filename = `${safeTitle} - ${cleanQual}${ext}`;
            window.triggerDirectDownload(finalDl, filename);
          } catch (err) {
            window.showToast('Download error: ' + err.message, 'error');
          } finally {
            btnDl.innerHTML = origText;
            btnDl.disabled = false;
          }
        });
      }

      modalQualitiesList.appendChild(item);
    });
  } else {
    modalQualitiesList.innerHTML = `
      <div class="empty-state" style="padding: 24px; text-align:center;">
        <i class="fa-solid fa-triangle-exclamation" style="font-size:2rem; color: #ffb800; margin-bottom:8px;"></i>
        <p>No streams found for this movie yet.</p>
      </div>
    `;
  }
}
window.renderQualitiesListOnly = (quals, dtls, mv) => renderQualitiesListContent(quals, dtls, mv);

/**
 * 2026 Cinejoy & Cinema Direct Download Resolution Picker Modal
 * Displays available direct download mirrors (4K UHD, 1080p FHD, 720p HD with file sizes and codecs)
 * allowing the user to select their desired resolution to Download or Leech to Drive.
 */
window.openCinejoyDownloadPicker = async function (movie, details, extraOptions = {}) {
  const targetMovie = movie || window.state?.selectedMovie || {};
  const currentDtls = details || window.currentMovieDetails || {};
  const modal = document.getElementById('movieDownloadModal');
  const titleEl = document.getElementById('movieDownloadTitle');
  const subtitleEl = document.getElementById('movieDownloadSubtitle');
  const listEl = document.getElementById('movieDownloadList');
  const btnClose = document.getElementById('btnCloseMovieDownload');

  if (!modal || !listEl) return;

  const rawTitle = currentDtls.title || targetMovie.title || 'Movie';
  const cleanTitle = rawTitle
    .replace(/\[.*?\]/g, '')
    .replace(/\(.*?\)/g, '')
    .replace(/sinhala.*$/i, '')
    .trim();

  const isTv = !!(currentDtls.isTv || targetMovie.isTv || (targetMovie.link && targetMovie.link.includes('/tv/')));
  const season = extraOptions.season || currentDtls.activeSeason || targetMovie.season || 1;
  const episode = extraOptions.episode || currentDtls.activeEpisode || targetMovie.episode || 1;
  const epLabel = isTv ? ` (S${season} E${episode})` : '';

  if (titleEl) titleEl.textContent = `Downloads for ${cleanTitle}${epLabel}`;
  if (subtitleEl) subtitleEl.textContent = 'Searching direct high-speed cloud mirrors...';

  // Render animated loading skeleton while mirrors load
  listEl.innerHTML = `
    <div style="display: flex; flex-direction: column; gap: 10px; padding: 6px 0;">
      <div style="background: rgba(255,255,255,0.05); height: 64px; border-radius: 12px; animation: pulse 1.5s infinite;"></div>
      <div style="background: rgba(255,255,255,0.05); height: 64px; border-radius: 12px; animation: pulse 1.5s infinite;"></div>
      <div style="background: rgba(255,255,255,0.05); height: 64px; border-radius: 12px; animation: pulse 1.5s infinite;"></div>
      <div style="text-align: center; color: #00f2fe; font-size: 0.82rem; font-weight: 600; padding-top: 4px;">
        <i class="fa-solid fa-circle-notch fa-spin"></i> Resolving 4K / 1080p / 720p Direct Cloud Mirrors...
      </div>
    </div>
  `;

  if (window.openModalWithHistory) {
    window.openModalWithHistory(modal);
  } else {
    modal.classList.remove('hidden');
  }

  // Close handlers
  if (btnClose) {
    btnClose.onclick = () => {
      if (window.closeModalWithHistory) window.closeModalWithHistory(modal);
      else modal.classList.add('hidden');
    };
  }

  modal.onclick = (e) => {
    if (e.target === modal) {
      if (window.closeModalWithHistory) window.closeModalWithHistory(modal);
      else modal.classList.add('hidden');
    }
  };

  // 1. Gather all direct download candidates
  let directLinks = [];

  // Check pre-cached direct downloads on details object
  if (currentDtls.directDownloads && currentDtls.directDownloads.length > 0) {
    directLinks = [...currentDtls.directDownloads];
  }

  // 2. Query Cinejoy Direct Download API (shegu.st) if needed
  let targetTmdb = targetMovie.tmdb || currentDtls.tmdb || '';
  if (!targetTmdb && targetMovie.id && targetMovie.id.startsWith('netflix_')) {
    targetTmdb = targetMovie.id.replace('netflix_', '');
  }
  if (!targetTmdb && targetMovie.link) {
    const tm = targetMovie.link.match(/\/(?:movie|tv)\/(\d+)/);
    if (tm) targetTmdb = tm[1];
  }

  if (!targetTmdb && cleanTitle.length > 1) {
    try {
      const sUrl = `https://api.themoviedb.org/3/search/multi?api_key=8476a7ab80ad76f0936744df0430e67c&query=${encodeURIComponent(cleanTitle)}`;
      const sRes = await fetch(sUrl).then(r => r.json()).catch(() => null);
      if (sRes?.results?.[0]?.id) {
        targetTmdb = String(sRes.results[0].id);
        targetMovie.tmdb = targetTmdb;
        currentDtls.tmdb = targetTmdb;
      }
    } catch (_) { }
  }

  if (targetTmdb) {
    try {
      const dlEndpoint = isTv
        ? `https://downloads.shegu.st/tv/${targetTmdb}/${season}/${episode}`
        : `https://downloads.shegu.st/movie/${targetTmdb}`;

      const dlRes = await fetch(dlEndpoint, {
        headers: { 'Referer': 'https://cinejoy.to/' }
      }).then(r => r.json()).catch(() => null);

      if (dlRes?.links?.length) {
        dlRes.links.forEach((l, idx) => {
          if (!directLinks.some(x => (x.url || x.downloadUrl) === l.url)) {
            const qNum = parseInt(l.quality, 10) || 1080;
            const resTag = qNum >= 2160 ? '4K UHD' : (qNum >= 1080 ? '1080p FHD' : `${qNum}p HD`);
            const cleanName = (l.name || '').replace(/^4K CINEJOY\s*/i, '').trim();
            const sizeStr = l.size || (qNum >= 2160 ? '6.5 GB' : '2.2 GB');

            let tags = '';
            const matchTags = l.name?.match(/\[(.*?)\]/g);
            if (matchTags && matchTags.length > 0) {
              tags = matchTags.map(t => t.replace(/[\[\]]/g, '')).filter(t => !t.includes('GB') && !t.includes('MB') && !t.includes('Server')).join(' • ');
            }
            if (!tags && l.filename) {
              tags = l.filename.replace(/^.*?-/, '').replace(/\.mkv$/i, '').trim();
            }

            directLinks.push({
              source: `Mirror #${idx + 1}`,
              name: l.name || `${cleanTitle} [${resTag}] (Mirror ${idx + 1})`,
              quality: qNum,
              resTag: resTag,
              size: sizeStr,
              url: l.url,
              downloadUrl: l.url,
              provider: 'Cinejoy Cloud',
              formatTags: tags || 'HIGH SPEED STREAM',
              ext: 'mkv',
              isDirect: true,
              mirrorIndex: idx + 1
            });
          }
        });
      }
    } catch (e) {
      console.warn('[Cinejoy Download Picker Fetch Error]', e);
    }
  }

  // Check qualities on details for other direct candidate links (PixelDrain, SinhalaSub, GDrive, usersdrive, etc.)
  if (currentDtls.qualities) {
    currentDtls.qualities.forEach(q => {
      const u = q.downloadUrl || q.streamUrl || '';
      const isCandidate = q.isDirect || u.includes('workers.dev') || u.includes('shegu.st') || u.includes('pixeldrain.com') || u.includes('dlserver') || (u.includes('usersdrive.com') && u.includes('/d/')) || (u.includes('userdrive.org') && u.includes('/d/')) || u.endsWith('.mkv') || u.endsWith('.mp4') || u.endsWith('.zip');
      const isEmbed = u.includes('cinejoy.to/watch') || u.includes('vidsrc2.ru') || u.includes('vidlink.pro') || u.includes('multiembed.mov') || u.includes('vidsrc');
      if (isCandidate && !isEmbed) {
        if (!directLinks.some(x => (x.url || x.downloadUrl) === u)) {
          const qStr = q.quality || '';
          const qNum = qStr.includes('4k') || qStr.includes('2160') ? 2160 : (qStr.includes('720') ? 720 : 1080);
          const resTag = qNum >= 2160 ? '4K UHD' : (qNum >= 1080 ? '1080p FHD' : `${qNum}p HD`);
          directLinks.push({
            source: q.serverName || q.provider || 'Direct Cloud',
            name: q.quality || cleanTitle,
            quality: qNum,
            resTag: resTag,
            size: q.size || 'HD',
            url: u,
            downloadUrl: u,
            provider: q.provider || 'Direct Cloud',
            formatTags: q.formatTags || (q.isZip ? 'ZIP Package' : 'Direct Pipe'),
            ext: q.ext || (u.includes('.mkv') ? 'mkv' : (u.includes('.zip') ? 'zip' : 'mp4')),
            isDirect: true
          });
        }
      }
    });
  }

  // Also query YTS mirrors if directLinks is still empty and it's a movie
  if (directLinks.length === 0 && !isTv) {
    // 1. First check if currentDtls.qualities already has torrents
    const existingTorrents = (currentDtls.qualities || targetMovie.torrents || []).filter(q => q.downloadUrl && (q.type === 'torrent' || q.type === 'magnet' || q.downloadUrl.startsWith('magnet:') || q.downloadUrl.includes('.torrent')));
    if (existingTorrents.length > 0) {
      existingTorrents.forEach(t => {
        const qNum = (t.quality?.includes('2160') || t.quality?.includes('4K')) ? 2160 : (t.quality?.includes('720') ? 720 : 1080);
        const resTag = qNum >= 2160 ? '4K UHD' : (qNum >= 1080 ? '1080p FHD' : `${qNum}p HD`);
        directLinks.push({
          source: 'YTS Cinema Pipe',
          name: t.quality || 'Torrent Stream',
          quality: qNum,
          resTag: resTag,
          size: t.size || '1.4 GB',
          url: t.downloadUrl,
          downloadUrl: t.downloadUrl,
          provider: t.provider || 'P2P Torrent',
          formatTags: 'P2P SWARM • FAST SEEDS',
          ext: t.downloadUrl.startsWith('magnet:') ? 'magnet' : 'torrent',
          isDirect: true
        });
      });
    } else {
      const ytsTerm = currentDtls.imdb || targetMovie.imdb || cleanTitle;
      const ytsMirrors = [
        'https://yts.mx/api/v2/list_movies.json',
        'https://yts.lt/api/v2/list_movies.json',
        'https://yts.ag/api/v2/list_movies.json',
        'https://yts.am/api/v2/list_movies.json',
        'https://yts.bz/api/v2/list_movies.json',
        'https://yts.gg/api/v2/list_movies.json'
      ];
      for (const mUrl of ytsMirrors) {
        try {
          const yRes = await fetch(`${mUrl}?query_term=${encodeURIComponent(ytsTerm)}&limit=1`, { signal: AbortSignal.timeout(3000) }).then(r => r.json()).catch(() => null);
          if (yRes?.data?.movies?.[0]?.torrents?.length) {
            const ym = yRes.data.movies[0];
            ym.torrents.forEach(t => {
              const magnet = `magnet:?xt=urn:btih:${t.hash}&dn=${encodeURIComponent(ym.title)}&tr=udp://open.demonii.com:1337/announce&tr=udp://tracker.openbittorrent.com:80&tr=udp://tracker.opentrackr.org:1337/announce&tr=udp://tracker.coppersurfer.tk:6969/announce`;
              const qNum = t.quality?.includes('2160') ? 2160 : (t.quality?.includes('720') ? 720 : 1080);
              const resTag = qNum >= 2160 ? '4K UHD' : (qNum >= 1080 ? '1080p FHD' : `${qNum}p HD`);
              directLinks.push({
                source: 'YTS Cinema Pipe',
                name: `${t.quality} ${t.type?.toUpperCase() || 'WEB'}`,
                quality: qNum,
                resTag: resTag,
                size: t.size || '1.5 GB',
                url: magnet,
                downloadUrl: magnet,
                provider: 'P2P Torrent',
                formatTags: `${t.type?.toUpperCase() || 'BLURAY'} • P2P SEEDING`,
                ext: 'torrent',
                isDirect: true
              });
            });
            break;
          }
        } catch (_) { }
      }
    }
  }

  // 2b. Also query cross-portal direct download mirrors (SinhalaSub, PirateLK, SubLK, YTS)
  try {
    const netflixMirrors = await resolveDirectDownloadsForNetflix(targetMovie, currentDtls.imdb || targetMovie.imdb, cleanTitle, season, episode);
    if (netflixMirrors && netflixMirrors.length > 0) {
      netflixMirrors.forEach(m => {
        const u = m.downloadUrl || m.streamUrl;
        if (u && !directLinks.some(x => (x.url || x.downloadUrl) === u)) {
          const qNum = m.quality?.includes('2160') || m.quality?.includes('4K') ? 2160 : (m.quality?.includes('720') ? 720 : 1080);
          const resTag = qNum >= 2160 ? '4K UHD' : (qNum >= 1080 ? '1080p FHD' : `${qNum}p HD`);
          directLinks.push({
            source: m.provider || m.serverName || 'Direct Cloud',
            name: m.quality || `${cleanTitle} [${resTag}]`,
            quality: qNum,
            resTag: resTag,
            size: m.size || 'HD',
            url: u,
            downloadUrl: u,
            provider: m.provider || 'Direct Cloud',
            formatTags: m.isDirect ? 'DIRECT CLOUD STREAM' : 'P2P SWARM',
            ext: m.ext || (u.includes('.mkv') ? 'mkv' : (u.includes('.zip') ? 'zip' : 'mp4')),
            isDirect: true
          });
        }
      });
    }
  } catch (e) {
    console.warn('[Cinejoy Download Picker Netflix Mirrors Error]', e);
  }

  // Cache resolved links on details
  currentDtls.directDownloads = directLinks;

  // 3. Render Direct Download Options
  if (directLinks.length === 0) {
    if (subtitleEl) subtitleEl.textContent = 'No direct links found';
    listEl.innerHTML = `
      <div style="text-align: center; padding: 28px 16px; color: #94a3b8;">
        <i class="fa-solid fa-circle-exclamation" style="font-size: 2.2rem; color: #f59e0b; margin-bottom: 12px; display: block;"></i>
        <h4 style="color: #f8fafc; margin: 0 0 6px 0; font-size: 1.05rem;">No Direct Download Mirrors Available</h4>
        <p style="font-size: 0.82rem; margin: 0 0 16px 0; line-height: 1.45;">Direct binary download mirrors are not indexed for this specific title. You can watch online instantly across our 3 VIP Cinema Servers!</p>
        <button id="btnDlFallbackWatch" style="background: linear-gradient(135deg, #00f2fe, #4facfe); border: none; color: #000; font-weight: 800; padding: 10px 22px; border-radius: 20px; cursor: pointer; font-size: 0.85rem;">
          <i class="fa-solid fa-play"></i> Watch Online in Player
        </button>
      </div>
    `;
    const btnFb = listEl.querySelector('#btnDlFallbackWatch');
    if (btnFb) {
      btnFb.onclick = () => {
        if (window.closeModalWithHistory) window.closeModalWithHistory(modal);
        else modal.classList.add('hidden');
      };
    }
    return;
  }

  if (subtitleEl) subtitleEl.textContent = `Found ${directLinks.length} links • Select quality to download:`;
  listEl.innerHTML = '';

  directLinks.forEach((item, itemIdx) => {
    const card = document.createElement('div');
    card.className = 'cinejoy-dl-card';
    card.id = `cinejoyDlCard_${itemIdx}`;
    card.style.cssText = 'background: rgba(255, 255, 255, 0.04); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 14px; padding: 12px 14px; display: flex; align-items: center; justify-content: space-between; gap: 10px; transition: all 0.2s ease;';

    const qNum = parseInt(item.quality, 10) || 1080;
    const resTag = item.resTag || (qNum >= 2160 ? '4K UHD' : (qNum >= 1080 ? '1080p FHD' : `${qNum}p HD`));
    let qColor = '#00f2fe';
    if (qNum >= 2160) qColor = '#fbbf24';
    else if (qNum <= 720) qColor = '#38ef7d';

    const isSinhalaSub = item.name?.toLowerCase().includes('sinhala') || item.provider?.toLowerCase().includes('sinhala') || item.source?.toLowerCase().includes('sinhala');

    card.innerHTML = `
      <div style="display: flex; flex-direction: column; gap: 4px; flex: 1; min-width: 0;">
        <div style="display: flex; align-items: center; gap: 6px; flex-wrap: wrap;">
          <span style="font-weight: 800; color: #f8fafc; font-size: 0.92rem; letter-spacing: 0.2px;">${item.source || 'Direct Stream'}</span>
          <span style="color: #475569; font-size: 0.8rem;">•</span>
          <span style="font-weight: 800; color: ${qColor}; font-size: 0.92rem;">${resTag}</span>
          <span style="color: #475569; font-size: 0.8rem;">•</span>
          <span style="color: #94a3b8; font-size: 0.85rem; font-weight: 600;">${item.size || 'HD'}</span>
        </div>
        <div style="display: flex; align-items: center; gap: 6px; flex-wrap: wrap;">
          <span style="font-size: 0.72rem; color: #94a3b8; font-weight: 600; text-transform: uppercase; letter-spacing: 0.4px; background: rgba(255,255,255,0.05); padding: 2px 7px; border-radius: 4px; border: 1px solid rgba(255,255,255,0.08);">${item.formatTags || 'MKV • HIGH SPEED'}</span>
          ${isSinhalaSub ? '<span style="font-size: 0.7rem; color: #c084fc; background: rgba(168,85,247,0.14); padding: 2px 7px; border-radius: 4px; border: 1px solid rgba(168,85,247,0.35); font-weight: 700;">සිංහල උපසිරැසි</span>' : ''}
          <span class="cinejoy-probe-status" style="font-size: 0.7rem; color: #94a3b8; background: rgba(255,255,255,0.04); padding: 2px 7px; border-radius: 4px; border: 1px solid rgba(255,255,255,0.08); font-weight: 700; display: inline-flex; align-items: center; gap: 4px;">
            <i class="fa-solid fa-spinner fa-spin" style="font-size: 0.65rem;"></i> Checking...
          </span>
        </div>
      </div>

      <div style="display: flex; align-items: center; gap: 8px; flex-shrink: 0;">
        <!-- Copy Link Button -->
        <button class="btn-cinejoy-copy" title="Copy Direct Link" style="width: 36px; height: 36px; border-radius: 10px; background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.12); color: #cbd5e1; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: all 0.2s;">
          <i class="fa-regular fa-copy"></i>
        </button>

        <!-- Leech to Google Drive Button -->
        <button class="btn-cinejoy-drive" title="Leech to Google Drive with 0 MB Mobile Data" style="height: 36px; padding: 0 12px; border-radius: 10px; background: rgba(0, 242, 254, 0.12); border: 1px solid rgba(0, 242, 254, 0.35); color: #00f2fe; display: flex; align-items: center; gap: 5px; font-size: 0.8rem; font-weight: 700; cursor: pointer; transition: all 0.2s;">
          <i class="fa-solid fa-cloud-arrow-up"></i>
          <span>Drive</span>
        </button>

        <!-- Download Pill Button -->
        <button class="btn-cinejoy-dl-action" title="Direct Download to Device Storage" style="height: 36px; padding: 0 16px; border-radius: 20px; background: #ffffff; color: #0f172a; border: none; display: flex; align-items: center; gap: 6px; font-size: 0.84rem; font-weight: 800; cursor: pointer; box-shadow: 0 2px 10px rgba(0,0,0,0.35); transition: all 0.2s;">
          <i class="fa-solid fa-download"></i>
          <span>Download</span>
        </button>
      </div>
    `;

    // Asynchronously probe mirror health in background
    const u = item.url || item.downloadUrl;
    const probeBadge = card.querySelector('.cinejoy-probe-status');

    if (u && (u.startsWith('http://') || u.startsWith('https://')) && !u.startsWith('magnet:')) {
      (async () => {
        try {
          let isAlive = false;
          let isQuota = false;
          if (window.Capacitor?.Plugins?.NativeExtractor?.probeMediaUrl) {
            const pRes = await window.Capacitor.Plugins.NativeExtractor.probeMediaUrl({ url: u });
            isAlive = !!pRes?.isAlive;
            isQuota = !!pRes?.isQuotaExceeded;
          } else {
            const p = await fetch(u, {
              headers: { 'Referer': 'https://cinejoy.to/', 'Range': 'bytes=0-10' },
              signal: AbortSignal.timeout(3500)
            }).catch(() => null);
            if (p) {
              isAlive = p.status === 206 || p.status === 200;
              isQuota = p.status === 403;
            }
          }

          item.isProbed = true;
          item.isAlive = isAlive;
          item.isQuotaExceeded = isQuota;

          if (isAlive) {
            probeBadge.innerHTML = '<i class="fa-solid fa-bolt" style="color: #22c55e;"></i> Active Mirror';
            probeBadge.style.color = '#4ade80';
            probeBadge.style.background = 'rgba(34, 197, 94, 0.12)';
            probeBadge.style.borderColor = 'rgba(34, 197, 94, 0.3)';
          } else if (isQuota) {
            probeBadge.innerHTML = '<i class="fa-solid fa-triangle-exclamation" style="color: #f59e0b;"></i> Daily GDrive Quota Exceeded';
            probeBadge.style.color = '#fbbf24';
            probeBadge.style.background = 'rgba(245, 158, 11, 0.14)';
            probeBadge.style.borderColor = 'rgba(245, 158, 11, 0.35)';
            card.style.opacity = '0.7';
          } else {
            probeBadge.innerHTML = '<i class="fa-solid fa-circle-check" style="color: #38bdf8;"></i> Ready';
            probeBadge.style.color = '#38bdf8';
          }
        } catch (_) {
          probeBadge.innerHTML = '<i class="fa-solid fa-circle-check" style="color: #38bdf8;"></i> Ready';
          probeBadge.style.color = '#38bdf8';
        }
      })();
    } else {
      if (probeBadge) probeBadge.style.display = 'none';
    }

    // Event listeners
    const btnCopy = card.querySelector('.btn-cinejoy-copy');
    if (btnCopy) {
      btnCopy.onclick = () => {
        const copyUrl = item.url || item.downloadUrl;
        if (navigator.clipboard?.writeText) {
          navigator.clipboard.writeText(copyUrl);
          window.showToast('📋 Direct download link copied!', 'success');
        } else {
          window.showToast('Link: ' + copyUrl, 'info');
        }
      };
    }

    const btnDrive = card.querySelector('.btn-cinejoy-drive');
    if (btnDrive) {
      btnDrive.onclick = () => {
        let finalUrl = item.url || item.downloadUrl;
        let finalResTag = resTag;
        if (item.isQuotaExceeded) {
          const alternate = directLinks.find(x => x.isAlive && (x.url !== finalUrl));
          if (alternate) {
            finalUrl = alternate.url || alternate.downloadUrl;
            finalResTag = alternate.resTag || resTag;
          }
        }
        if (window.startCloudTransfer) {
          window.startCloudTransfer({
            title: `${cleanTitle}${epLabel} [${finalResTag}]`,
            url: finalUrl,
            type: isTv ? 'series' : 'movie',
            quality: finalResTag
          });
        }
        if (window.closeModalWithHistory) window.closeModalWithHistory(modal);
        else modal.classList.add('hidden');
      };
    }

    const btnDlAct = card.querySelector('.btn-cinejoy-dl-action');
    if (btnDlAct) {
      btnDlAct.onclick = async () => {
        let finalUrl = item.url || item.downloadUrl;
        let finalResTag = resTag;

        if (item.isQuotaExceeded) {
          const alternate = directLinks.find(x => x.isAlive && (x.url !== finalUrl));
          if (alternate) {
            window.showToast(`⚠️ Mirror quota reached. Auto-switching to Active ${alternate.resTag || alternate.source}!`, 'warning');
            finalUrl = alternate.url || alternate.downloadUrl;
            finalResTag = alternate.resTag || resTag;
          } else {
            window.showToast('⚠️ Google Drive daily quota exceeded on this mirror today. Please choose another mirror or watch online!', 'error');
            return;
          }
        }

        const safeTitle = `${cleanTitle}${epLabel}`.replace(/[/\\?%*:|"<>]/g, '_');
        const ext = finalUrl.includes('.mkv') ? '.mkv' : (finalUrl.includes('.zip') ? '.zip' : '.mp4');
        const filename = `${safeTitle} - ${finalResTag}${ext}`;
        window.showToast(`⚡ Starting direct download: ${finalResTag} (${item.size || ''})`, 'success');
        window.triggerDirectDownload(finalUrl, filename);
        if (window.closeModalWithHistory) window.closeModalWithHistory(modal);
        else modal.classList.add('hidden');
      };
    }

    listEl.appendChild(card);
  });
};

/**
 * Resolves real direct download links (PixelDrain, Google Drive, DLServer, Torrents)
 * for Netflix releases by querying available portals (SinhalaSub, PirateLK, YTS, SubLK).
 */
async function resolveDirectDownloadsForNetflix(movie, imdbCode, cleanTitle, seasonNum = 1, episodeNum = 1) {
  const directQuals = [];
  const cleanQ = (cleanTitle || movie?.title || '')
    .replace(/\[.*?\]/g, '')
    .replace(/\(.*?\)/g, '')
    .replace(/season.*$/i, '')
    .trim();

  if (!cleanQ && !movie) return directQuals;
  const normTitle = cleanQ.toLowerCase().replace(/[^a-z0-9]/g, '');

  // 0. Primary High-Speed Authentic Cinejoy Direct Cloud Downloads (4K UHD, 1080p FHD, 720p HD)
  let targetTmdb = movie?.tmdb || '';
  const movieLink = movie?.link || '';
  const movieId = movie?.id || '';

  const tmdbMatch = movieLink.match(/\/(?:movie|tv)\/(\d+)/) || movieId.match(/^netflix_(\d+)$/);
  if (tmdbMatch) targetTmdb = tmdbMatch[1];

  if (!targetTmdb && typeof KNOWN_NETFLIX_IMDB_MAP !== 'undefined') {
    for (const [k, v] of Object.entries(KNOWN_NETFLIX_IMDB_MAP)) {
      const kNorm = k.replace(/[^a-z0-9]/g, '');
      if (normTitle === kNorm || (kNorm.length > 4 && (normTitle.includes(kNorm) || kNorm.includes(normTitle)))) {
        if (v.tmdb) targetTmdb = v.tmdb;
        break;
      }
    }
  }

  // Active lookup if TMDB ID missing
  if (!targetTmdb && cleanQ.length > 1) {
    try {
      const sUrl = `https://api.themoviedb.org/3/search/multi?api_key=8476a7ab80ad76f0936744df0430e67c&query=${encodeURIComponent(cleanQ)}`;
      const sRes = await fetch(sUrl).then(r => r.json()).catch(() => null);
      if (sRes?.results?.[0]?.id) {
        targetTmdb = String(sRes.results[0].id);
        if (movie) movie.tmdb = targetTmdb;
      }
    } catch (_) { }
  }

  if (targetTmdb) {
    try {
      const isTv = !!movie?.isTv || movieLink.includes('/tv/');
      const season = seasonNum || movie?.season || 1;
      const episode = episodeNum || movie?.episode || 1;
      const dlEndpoint = isTv
        ? `https://downloads.shegu.st/tv/${targetTmdb}/${season}/${episode}`
        : `https://downloads.shegu.st/movie/${targetTmdb}`;

      const dlRes = await fetch(dlEndpoint, {
        headers: { 'Referer': 'https://cinejoy.to/' }
      }).then(r => r.json()).catch(() => null);

      if (dlRes?.links?.length) {
        dlRes.links.forEach(l => {
          const qNum = parseInt(l.quality, 10) || 1080;
          const resTag = qNum >= 2160 ? '4K UHD' : (qNum >= 1080 ? '1080p FHD' : `${qNum}p HD`);
          const cleanName = (l.name || '').replace(/^4K CINEJOY\s*/i, '').trim();
          const sizeStr = l.size || (qNum >= 2160 ? '6.5 GB' : '2.2 GB');

          directQuals.push({
            quality: `⚡ Cinejoy Direct DL: ${resTag} (${sizeStr})`,
            serverTitle: `⚡ Cinejoy Direct DL: ${resTag} (${sizeStr})`,
            provider: `Cinejoy Direct Cloud • ${cleanName || resTag}`,
            downloadUrl: l.url,
            streamUrl: l.url,
            size: sizeStr,
            type: 'video',
            isDirect: true,
            ext: 'mkv',
            serverName: 'Cinejoy Direct'
          });
        });
      }
    } catch (e) {
      console.warn('[Cinejoy Direct DL Fetch Error]', e);
    }
  }

  // 1. Query YTS high-speed mirrors using IMDb ID or title for authentic 4K/1080p/720p P2P & downloads
  const ytsTerm = imdbCode || cleanQ;
  if (ytsTerm) {
    const ytsMirrors = [
      'https://yts.lt/api/v2/list_movies.json',
      'https://yts.ag/api/v2/list_movies.json',
      'https://yts.am/api/v2/list_movies.json',
      'https://yts.bz/api/v2/list_movies.json'
    ];
    for (const mUrl of ytsMirrors) {
      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 3500);
        const res = await fetch(`${mUrl}?query_term=${encodeURIComponent(ytsTerm)}`, { signal: controller.signal })
          .then(r => r.json())
          .finally(() => clearTimeout(timeoutId));

        if (res?.data?.movies?.[0]?.torrents?.length) {
          const ytsMovie = res.data.movies[0];
          for (const t of ytsMovie.torrents) {
            const magnet = `magnet:?xt=urn:btih:${t.hash}&dn=${encodeURIComponent(ytsMovie.title)}&tr=udp://open.demonii.com:1337/announce&tr=udp://tracker.openbittorrent.com:80&tr=udp://tracker.opentrackr.org:1337/announce&tr=udp://tracker.coppersurfer.tk:6969/announce`;
            directQuals.push({
              quality: `${t.quality} ${t.type?.toUpperCase() || 'WEB'} (Direct P2P)`,
              provider: 'Direct Cinema Pipe (YTS)',
              downloadUrl: magnet,
              streamUrl: magnet,
              size: t.size || '1.5 GB',
              type: 'torrent',
              isZip: false,
              ext: 'torrent'
            });
          }
          break;
        }
      } catch (_) { }
    }
  }

  return directQuals;
}
window.resolveDirectDownloadsForNetflix = resolveDirectDownloadsForNetflix;

// In-Memory Fast Caches for Ultra-Fast Instant Reopening (0ms Delay)
const netflixDetailsMemoryCache = new Map();
const ytsDetailsMemoryCache = new Map();

/**
 * Main Entry Point: Opens movie modal, checks disk cache, or dispatches scraper
 */
window.openMovieModal = async function (movie, cachedDetails, forceFresh = false) {
  if (!movie) return;

  // Central Feature Access Gatekeeper (Master Prompt Section 13 & 14)
  if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('TAB_MOVIES')) {
    if (window.showFeatureLockedSheet) window.showFeatureLockedSheet('TAB_MOVIES');
    return;
  }

  const thisRequestId = ++activeMovieRequestId;
  currentActiveMovieUrl = movie.link || movie.id || movie.title || '';
  window.state.selectedMovie = movie;
  window._activeMovieReturnContext = null;

  if (modalQualitiesList) modalQualitiesList.innerHTML = '';

  if (modalBackdropImg) {
    modalBackdropImg.setAttribute('referrerpolicy', 'no-referrer');
    modalBackdropImg.src = movie.poster || '';
  }
  if (modalMoviePoster) {
    modalMoviePoster.setAttribute('referrerpolicy', 'no-referrer');
    modalMoviePoster.src = movie.poster || '';
  }
  if (modalMovieTitle) modalMovieTitle.textContent = formatMovieCardTitle(movie.title);
  if (modalSourceBadge) modalSourceBadge.textContent = movie.source || 'Sinhalasub';
  if (modalMovieYear) modalMovieYear.innerHTML = `<i class="fa-solid fa-calendar"></i> ${movie.year || '2026'}`;
  if (modalMovieRating) {
    const score = formatRatingNumber(movie.rating, movie.title);
    modalMovieRating.innerHTML = `<i class="fa-solid fa-star" style="color: #f5c518;"></i> ${score} <span style="font-size: 0.8em; opacity: 0.85; margin-left: 2px;">IMDb</span>`;
  }

  setupModalWatchlistAndTrailer(movie, null);

  // ⚡ INSTANT POPUP (<50ms UX): Open modal immediately on card tap!
  if (window.openModalWithHistory) window.openModalWithHistory(movieModal);
  else if (movieModal) movieModal.classList.remove('hidden');

  if (modalQualitiesList) {
    modalQualitiesList.innerHTML = `
      <div class="stream-resolving-loader">
        <i class="fa-solid fa-circle-notch fa-spin"></i>
        <span>Connecting High-Speed Streams & Mirrors...</span>
      </div>
    `;
  }

  // ⚡ Fast Pre-Cached Lookup for Famous Netflix Originals & Global Releases
  const KNOWN_NETFLIX_IMDB_MAP = {
    'squid game': { id: 'tt10919420', tmdb: '93405', isTv: true },
    'squid game: the challenge': { id: 'tt28104766', tmdb: '204541', isTv: true },
    'stranger things': { id: 'tt4574334', tmdb: '66732', isTv: true },
    'wednesday': { id: 'tt13443470', tmdb: '119051', isTv: true },
    'money heist': { id: 'tt6468322', tmdb: '71446', isTv: true },
    'peaky blinders': { id: 'tt2442560', tmdb: '60574', isTv: true },
    'avatar: the last airbender': { id: 'tt9018736', tmdb: '82452', isTv: true },
    'one piece': { id: 'tt11737520', tmdb: '111110', isTv: true },
    'cobra kai': { id: 'tt7221388', tmdb: '77169', isTv: true },
    'the witcher': { id: 'tt5180504', tmdb: '71912', isTv: true },
    'black mirror': { id: 'tt2085059', tmdb: '42009', isTv: true },
    'arcane': { id: 'tt11126994', tmdb: '94605', isTv: true },
    'lupin': { id: 'tt2531336', tmdb: '96677', isTv: true },
    'bridgerton': { id: 'tt8740790', tmdb: '91363', isTv: true },
    'all of us are dead': { id: 'tt12475482', tmdb: '99966', isTv: true },
    'alice in borderland': { id: 'tt10795658', tmdb: '110316', isTv: true },
    'dark': { id: 'tt5753856', tmdb: '70523', isTv: true },
    'the queen\'s gambit': { id: 'tt10048342', tmdb: '87739', isTv: true },
    'heartstopper': { id: 'tt10638036', tmdb: '124834', isTv: true },
    'red notice': { id: 'tt7991608', tmdb: '512195', isTv: false },
    'the electric state': { id: 'tt7766378', tmdb: '799766', isTv: false },
    'army of the dead': { id: 'tt0993840', tmdb: '581644', isTv: false },
    'spenser confidential': { id: 'tt8629748', tmdb: '581600', isTv: false },
    'triple frontier': { id: 'tt1488604', tmdb: '399579', isTv: false },
    'the woman in cabin 10': { id: 'tt32420959', tmdb: '1284568', isTv: false },
    'day shift': { id: 'tt13314558', tmdb: '755566', isTv: false },
    'bird box': { id: 'tt2737304', tmdb: '405774', isTv: false },
    'me time': { id: 'tt14235882', tmdb: '848278', isTv: false },
    'the old guard': { id: 'tt7556122', tmdb: '547016', isTv: false },
    'the old guard 2': { id: 'tt13970428', tmdb: '790493', isTv: false },
    'voicemails for isabelle': { id: 'tt10850238', tmdb: '631244', isTv: false },
    'little house on the prairie': { id: 'tt0071015', tmdb: '1834', isTv: true },
    'wwe raw': { id: 'tt0185107', tmdb: '2261', isTv: true },
    'extraction': { id: 'tt8936646', tmdb: '545609', isTv: false },
    'extraction 2': { id: 'tt12263384', tmdb: '697843', isTv: false },
    'the gray man': { id: 'tt1649418', tmdb: '725201', isTv: false },
    'glass onion': { id: 'tt11564570', tmdb: '661374', isTv: false },
    'damsel': { id: 'tt13452446', tmdb: '763215', isTv: false },
    'atlas': { id: 'tt14856980', tmdb: '823464', isTv: false }
  };

  // ⚡ 0. Instant Netflix Direct Details
  if (movie.isNetflix || (movie.source || '').toLowerCase() === 'netflix' || (movie.link || '').includes('netflix.com')) {
    const netflixCacheKey = (movie.id || movie.title || '').toLowerCase().trim();
    if (netflixDetailsMemoryCache.has(netflixCacheKey) && !forceFresh) {
      const cached = netflixDetailsMemoryCache.get(netflixCacheKey);
      renderDetailsUi(cached, movie);
      setupSeriesEpisodes(movie, cached);
      return;
    }

    let imdbCode = movie.imdb || '';
    let tmdbCode = movie.tmdb || '';
    let isTv = !!movie.isTv;

    // Instant offline lookup for top hits
    const normKey = (movie.title || '').toLowerCase().replace(/[^a-z0-9]/g, '');
    for (const [k, v] of Object.entries(KNOWN_NETFLIX_IMDB_MAP)) {
      const kNorm = k.replace(/[^a-z0-9]/g, '');
      if (normKey === kNorm || (kNorm.length > 4 && (normKey.includes(kNorm) || kNorm.includes(normKey)))) {
        if (!imdbCode) imdbCode = v.id;
        if (!tmdbCode && v.tmdb) tmdbCode = v.tmdb;
        isTv = v.isTv;
        break;
      }
    }

    let targetTmdbCode = movie.tmdb || tmdbCode || (movie.id && movie.id.startsWith('netflix_') ? movie.id.replace('netflix_', '') : '') || (movie.link?.match(/\/(?:movie|tv)\/(\d+)/)?.[1]) || '';

    const cleanTitle = (movie.title || '')
      .replace(/\[.*?\]/g, '')
      .replace(/\(.*?\)/g, '')
      .replace(/season.*$/i, '')
      .trim();

    const buildNetflixQualities = (curTmdb, curImdb) => {
      const targetTitleEncoded = encodeURIComponent(cleanTitle || 'Movie');
      const sVidSrc2 = isTv
        ? (curTmdb ? `https://vidsrc2.ru/embed/tv/${curTmdb}/1/1` : `https://vidsrc2.ru/embed/tv/${targetTitleEncoded}/1/1`)
        : `https://vidsrc2.ru/embed/movie/${curTmdb || curImdb || targetTitleEncoded}`;

      const sCinejoy = isTv
        ? (curTmdb ? `https://cinejoy.to/watch/tv/${curTmdb}/1/1` : (curImdb ? `https://cinejoy.to/watch/tv/${curImdb}/1/1` : `https://cinejoy.to/search`))
        : (curTmdb ? `https://cinejoy.to/watch/movie/${curTmdb}` : (curImdb ? `https://cinejoy.to/watch/movie/${curImdb}` : `https://cinejoy.to/search`));

      return [
        {
          quality: '🌟 Server 1: VidSrc2 Pro – 1080p Full HD • Multi-Audio',
          provider: 'VidSrc2 Pro',
          downloadUrl: sVidSrc2,
          streamUrl: sVidSrc2,
          size: movie.runtime ? `Runtime: ${movie.runtime}` : '1080p Full HD',
          type: 'embed',
          serverName: 'VidSrc2 Pro'
        },
        {
          quality: '🚀 Server 2: Cinejoy VIP – 4K UHD / 1080p Stream',
          provider: 'Cinejoy VIP',
          downloadUrl: sCinejoy,
          streamUrl: sCinejoy,
          size: movie.runtime ? `Runtime: ${movie.runtime}` : '4K / 1080p Ultra HD',
          type: 'embed',
          serverName: 'Cinejoy VIP'
        }
      ];
    };

    const initialNetflixDetails = {
      title: movie.title,
      poster: movie.poster,
      rating: movie.rating || '',
      year: movie.year || '',
      imdb: imdbCode,
      tmdb: targetTmdbCode,
      isTv: isTv,
      source: 'Netflix',
      runtime: movie.runtime || 'Full Release',
      synopsis: movie.synopsis || `${movie.title} - Netflix Original. Stream in 1080p FHD & 4K UHD via VidSrc2 Pro & Cinejoy VIP Player.`,
      qualities: buildNetflixQualities(targetTmdbCode, imdbCode)
    };

    // ⚡ 0ms Render: Streams are immediately ready to play
    renderDetailsUi(initialNetflixDetails, movie);
    setupSeriesEpisodes(movie, initialNetflixDetails);

    // Fast parallel background resolution for TMDB/IMDb codes & episodes
    (async () => {
      let updated = false;
      if (!imdbCode) {
        try {
          const type = isTv ? 'series' : 'movie';
          const altType = isTv ? 'movie' : 'series';
          const [res1, res2] = await Promise.all([
            fetch(`https://v3-cinemeta.strem.io/catalog/${type}/top/search=${encodeURIComponent(cleanTitle)}.json`, { signal: AbortSignal.timeout(1800) }).then(r => r.json()).catch(() => null),
            fetch(`https://v3-cinemeta.strem.io/catalog/${altType}/top/search=${encodeURIComponent(cleanTitle)}.json`, { signal: AbortSignal.timeout(1800) }).then(r => r.json()).catch(() => null)
          ]);
          const meta = res1?.metas?.[0] || res2?.metas?.[0];
          if (meta) {
            imdbCode = meta.imdb_id || meta.id || imdbCode;
            if (meta.type === 'series') isTv = true;
            updated = true;
          }
        } catch (_) { }
      }

      if (!targetTmdbCode && imdbCode) {
        try {
          const fUrl = `https://api.themoviedb.org/3/find/${imdbCode}?api_key=8476a7ab80ad76f0936744df0430e67c&external_source=imdb_id`;
          const fRes = await fetch(fUrl, { signal: AbortSignal.timeout(1800) }).then(r => r.json()).catch(() => null);
          const fItem = isTv ? (fRes?.tv_results?.[0] || fRes?.movie_results?.[0]) : (fRes?.movie_results?.[0] || fRes?.tv_results?.[0]);
          if (fItem?.id) {
            targetTmdbCode = String(fItem.id);
            updated = true;
          }
        } catch (_) { }
      }

      movie.imdb = imdbCode;
      movie.tmdb = targetTmdbCode;
      movie.isTv = isTv;

      const finalDetails = {
        ...initialNetflixDetails,
        imdb: imdbCode,
        tmdb: targetTmdbCode,
        isTv: isTv,
        qualities: buildNetflixQualities(targetTmdbCode, imdbCode)
      };

      netflixDetailsMemoryCache.set(netflixCacheKey, finalDetails);

      if (thisRequestId === activeMovieRequestId && updated) {
        renderDetailsUi(finalDetails, movie);
        setupSeriesEpisodes(movie, finalDetails);
      }
    })();

    return;
  }

  // ⚡ 0b. Instant YTS 4K Direct Details (0ms Delay, Instant Torrents & Cinejoy VIP)
  if ((movie.source || '').toLowerCase().includes('yts') || (movie.link || '').includes('yts.') || (movie.link || '').includes('yts.mx')) {
    const ytsCacheKey = (movie.id || movie.imdb || movie.title || '').toLowerCase().trim();
    if (ytsDetailsMemoryCache.has(ytsCacheKey) && !forceFresh) {
      const cached = ytsDetailsMemoryCache.get(ytsCacheKey);
      renderDetailsUi(cached, movie);
      return;
    }

    let imdbCode = movie.imdb || (movie.link?.match(/imdb=(tt\d+)/)?.[1]) || (movie.link?.match(/tt\d+/)?.[0]) || '';
    const cleanMovieTitle = (movie.title || 'Movie').replace(/\[.*?\]/g, '').replace(/\(.*?\)/g, '').trim();

    let torrents = movie.torrents || [];
    if ((!torrents || torrents.length === 0) && movie.torrentsJson) {
      try {
        torrents = typeof movie.torrentsJson === 'string' ? JSON.parse(movie.torrentsJson) : movie.torrentsJson;
      } catch (_) { }
    }
    let movieSynopsis = movie.synopsis || '';
    let movieRuntime = movie.runtime ? `${movie.runtime} min` : '';
    let tmdbCode = movie.tmdb || '';

    const buildYtsQualities = (curTorrents, curTmdb, curImdb) => {
      const sVidSrc2 = curTmdb
        ? `https://vidsrc2.ru/embed/movie/${curTmdb}`
        : (curImdb ? `https://vidsrc2.ru/embed/movie/${curImdb}` : `https://vidsrc2.ru/embed/movie/${encodeURIComponent(cleanMovieTitle)}`);
      const sCinejoy = curTmdb
        ? `https://cinejoy.to/watch/movie/${curTmdb}`
        : (curImdb ? `https://cinejoy.to/watch/movie/${curImdb}` : (sVidSrc2 || `https://cinejoy.to/search`));

      const list = [
        {
          quality: '🌟 Server 1: VidSrc2 Pro – 1080p Full HD • Multi-Audio',
          provider: 'VidSrc2 Pro',
          downloadUrl: sVidSrc2,
          streamUrl: sVidSrc2,
          size: movieRuntime || '1080p Full HD',
          type: 'embed',
          serverName: 'VidSrc2 Pro'
        },
        {
          quality: '🚀 Server 2: Cinejoy VIP – 4K UHD / 1080p Stream',
          provider: 'Cinejoy VIP',
          downloadUrl: sCinejoy,
          streamUrl: sCinejoy,
          size: movieRuntime || '4K / 1080p Ultra HD',
          type: 'embed',
          serverName: 'Cinejoy VIP'
        }
      ];

      for (const t of curTorrents) {
        const qNum = t.quality || '1080p';
        const tType = t.type ? ` (${t.type.toUpperCase()})` : '';
        const qualLabel = `${qNum}${tType}`;
        const magnet = `magnet:?xt=urn:btih:${t.hash}&dn=${encodeURIComponent(movie.title)}&tr=udp://open.demonii.com:1337/announce&tr=udp://tracker.openbittorrent.com:80&tr=udp://tracker.opentrackr.org:1337/announce&tr=udp://tracker.coppersurfer.tk:6969/announce`;
        const tUrl = t.url || `https://yts.mx/torrent/download/${t.hash}`;

        list.push({
          quality: `${qualLabel} - Direct Torrent File`,
          provider: `YTS High-Speed Torrent (${t.size || '1.4 GB'})`,
          downloadUrl: tUrl,
          streamUrl: magnet,
          size: t.size || '1.4 GB',
          type: 'torrent',
          ext: 'torrent'
        });

        list.push({
          quality: `${qualLabel} - 1-Click Magnet URI`,
          provider: `YTS Magnet Pipe (${t.size || '1.4 GB'})`,
          downloadUrl: magnet,
          streamUrl: magnet,
          size: t.size || '1.4 GB',
          type: 'magnet',
          ext: 'magnet'
        });
      }

      return list;
    };

    // If torrents already pre-attached on the movie card object, render in 0ms!
    if (torrents && torrents.length > 0) {
      const ytsDetails = {
        title: movie.title,
        poster: movie.poster,
        rating: movie.rating || '',
        year: movie.year || '',
        imdb: imdbCode,
        tmdb: tmdbCode,
        source: 'YTS 4K',
        runtime: movieRuntime,
        synopsis: movieSynopsis || `${movie.title} - High-Speed 4K Cinema Stream & Downloads via YTS.`,
        qualities: buildYtsQualities(torrents, tmdbCode, imdbCode)
      };
      ytsDetailsMemoryCache.set(ytsCacheKey, ytsDetails);
      renderDetailsUi(ytsDetails, movie);
      return;
    }

    // Initial render with Server 1 & Server 2 (0ms ready to stream!)
    const initialYtsDetails = {
      title: movie.title,
      poster: movie.poster,
      rating: movie.rating || '',
      year: movie.year || '',
      imdb: imdbCode,
      tmdb: tmdbCode,
      source: 'YTS 4K',
      runtime: movieRuntime,
      synopsis: movieSynopsis || `${movie.title} - High-Speed 4K Cinema Stream & Downloads via YTS.`,
      qualities: buildYtsQualities([], tmdbCode, imdbCode)
    };
    renderDetailsUi(initialYtsDetails, movie);

    // Show a sleek mini-loader below Server 1 & Server 2 indicating torrents are resolving
    if (modalQualitiesList) {
      const loaderCard = document.createElement('div');
      loaderCard.className = 'stream-resolving-loader';
      loaderCard.innerHTML = `
        <i class="fa-solid fa-circle-notch fa-spin"></i>
        <span>Connecting YTS 4K & 1080p Torrent Swarms...</span>
      `;
      modalQualitiesList.appendChild(loaderCard);
    }

    // Background fetch: Native Extractor with DoH first (bypasses ISP blocks), then fallback to JS mirrors
    (async () => {
      let nativeResolved = null;
      if (window.Capacitor?.Plugins?.NativeExtractor?.getMovieDetails) {
        try {
          const nRes = await window.Capacitor.Plugins.NativeExtractor.getMovieDetails({ url: movie.link || movie.id || movie.title });
          if (nRes?.details?.qualities && nRes.details.qualities.length > 0) {
            nativeResolved = nRes.details;
          }
        } catch (e) {
          console.warn('[YTS NativeExtractor error]', e);
        }
      }

      if (nativeResolved && nativeResolved.qualities && nativeResolved.qualities.length > 0) {
        const finalYtsDetails = {
          title: movie.title,
          poster: movie.poster,
          rating: movie.rating || nativeResolved.rating || '',
          year: movie.year || nativeResolved.year || '',
          imdb: imdbCode,
          tmdb: tmdbCode,
          source: 'YTS 4K',
          runtime: movieRuntime || (nativeResolved.duration ? `${nativeResolved.duration} min` : ''),
          synopsis: nativeResolved.synopsis || movieSynopsis || `${movie.title} - High-Speed 4K Cinema Stream & Downloads via YTS.`,
          qualities: nativeResolved.qualities
        };

        ytsDetailsMemoryCache.set(ytsCacheKey, finalYtsDetails);
        if (thisRequestId === activeMovieRequestId) {
          renderDetailsUi(finalYtsDetails, movie);
        }
        return;
      }

      const qTerm = imdbCode || cleanMovieTitle;
      const ytsMirrors = [
        'https://yts.mx/api/v2/list_movies.json',
        'https://yts.lt/api/v2/list_movies.json',
        'https://yts.ag/api/v2/list_movies.json'
      ];

      const fetchMirror = async (mUrl) => {
        const res = await fetch(`${mUrl}?query_term=${encodeURIComponent(qTerm)}&limit=1`, { signal: AbortSignal.timeout(2400) });
        if (!res.ok) throw new Error('YTS fail');
        const data = await res.json();
        if (data?.data?.movies?.[0]) return data.data.movies[0];
        throw new Error('No movie');
      };

      try {
        const yObj = await Promise.any(ytsMirrors.map(u => fetchMirror(u)));
        if (yObj) {
          if (!imdbCode && yObj.imdb_code) imdbCode = yObj.imdb_code;
          torrents = yObj.torrents || [];
          if (!movieSynopsis) movieSynopsis = yObj.description_full || yObj.summary || '';
          if (!movieRuntime && yObj.runtime) movieRuntime = `${yObj.runtime} min`;
        }
      } catch (_) { }

      // Fast TMDB find if imdbCode is available
      if (!tmdbCode && imdbCode) {
        try {
          const findUrl = `https://api.themoviedb.org/3/find/${imdbCode}?api_key=8476a7ab80ad76f0936744df0430e67c&external_source=imdb_id`;
          const findRes = await fetch(findUrl, { signal: AbortSignal.timeout(1800) }).then(r => r.json()).catch(() => null);
          if (findRes?.movie_results?.[0]?.id) {
            tmdbCode = String(findRes.movie_results[0].id);
          }
        } catch (_) { }
      }

      const finalYtsDetails = {
        title: movie.title,
        poster: movie.poster,
        rating: movie.rating || '',
        year: movie.year || '',
        imdb: imdbCode,
        tmdb: tmdbCode,
        source: 'YTS 4K',
        runtime: movieRuntime,
        synopsis: movieSynopsis || `${movie.title} - High-Speed 4K Cinema Stream & Downloads via YTS.`,
        qualities: buildYtsQualities(torrents, tmdbCode, imdbCode)
      };

      ytsDetailsMemoryCache.set(ytsCacheKey, finalYtsDetails);

      if (thisRequestId === activeMovieRequestId) {
        renderDetailsUi(finalYtsDetails, movie);
      }
    })();

    return;
  }


  // Cached Details Instant Render (e.g. returning from video player)
  if (cachedDetails && !forceFresh) {
    if (thisRequestId !== activeMovieRequestId) return;
    if (window.openModalWithHistory) window.openModalWithHistory(movieModal);
    else if (movieModal) movieModal.classList.remove('hidden');
    renderDetailsUi(cachedDetails, movie);
    return;
  }

  // ⚡ 0 MB RAM Local Storage (Disk Cache) check
  if (!forceFresh) {
    const diskDetails = window.getMovieDetailsDiskCache(movie.link, movie.source);
    if (diskDetails) {
      if (thisRequestId !== activeMovieRequestId) return;
      if (window.openModalWithHistory) window.openModalWithHistory(movieModal);
      else if (movieModal) movieModal.classList.remove('hidden');
      renderDetailsUi(diskDetails, movie);
      return;
    }
  } else {
    if (window.removeMovieDetailsDiskCache) {
      window.removeMovieDetailsDiskCache(movie.link, movie.source);
    }
  }

  if (modalMovieSynopsis) {
    modalMovieSynopsis.textContent = 'Fetching direct cloud stream links and 1080p/4K qualities...';
    modalMovieSynopsis.classList.remove('expanded');
  }
  if (btnToggleSynopsis) {
    btnToggleSynopsis.classList.add('hidden');
    btnToggleSynopsis.classList.remove('expanded');
    btnToggleSynopsis.innerHTML = `<span>Read More</span> <i class="fa-solid fa-chevron-down"></i>`;
  }

  // Skeleton Shimmer Loading
  if (modalQualitiesList) {
    modalQualitiesList.innerHTML = `
      <div class="quality-skeleton-card">
        <div class="skeleton-badge skeleton-shimmer"></div>
        <div class="skeleton-text-group">
          <div class="skeleton-text-line skeleton-shimmer" style="width: 50%;"></div>
          <div class="skeleton-text-line skeleton-shimmer" style="width: 35%;"></div>
        </div>
        <div class="skeleton-btns">
          <div class="skeleton-btn skeleton-shimmer" style="width: 70px;"></div>
          <div class="skeleton-btn skeleton-shimmer" style="width: 90px;"></div>
        </div>
      </div>
      <div class="quality-skeleton-card">
        <div class="skeleton-badge skeleton-shimmer"></div>
        <div class="skeleton-text-group">
          <div class="skeleton-text-line skeleton-shimmer" style="width: 60%;"></div>
          <div class="skeleton-text-line skeleton-shimmer" style="width: 40%;"></div>
        </div>
        <div class="skeleton-btns">
          <div class="skeleton-btn skeleton-shimmer" style="width: 70px;"></div>
          <div class="skeleton-btn skeleton-shimmer" style="width: 90px;"></div>
        </div>
      </div>
    `;
  }

  if (window.openModalWithHistory) window.openModalWithHistory(movieModal);
  else if (movieModal) movieModal.classList.remove('hidden');

  try {
    let details = null;

    // ⚡ 1. Direct Kotlin Native Extractor (100% On-Device Kotlin)
    if (window.Capacitor?.Plugins?.NativeExtractor?.getMovieDetails) {
      try {
        const nRes = await window.Capacitor.Plugins.NativeExtractor.getMovieDetails({ url: movie.link });
        if (thisRequestId !== activeMovieRequestId) {
          console.log('[movies.js] Stale NativeExtractor response discarded for:', movie.title);
          return;
        }
        if (nRes && nRes.details && nRes.details.qualities && nRes.details.qualities.length > 0) {
          details = nRes.details;
        } else if (nRes && nRes.qualities && nRes.qualities.length > 0) {
          details = nRes;
        }
      } catch (err) {
        console.warn('[Kotlin Native Engine] getMovieDetails fallback:', err);
      }
    }

    if (thisRequestId !== activeMovieRequestId) return;

    // ⚡ 2. Standalone Client-Side Scraper Fallback
    if ((!details || !details.qualities || details.qualities.length === 0) && window.StandaloneEngine?.getMovieDetails) {
      try {
        const sDetails = await window.StandaloneEngine.getMovieDetails(movie.link);
        if (thisRequestId !== activeMovieRequestId) return;
        if (sDetails && sDetails.qualities && sDetails.qualities.length > 0) {
          details = sDetails;
        } else if (!details) {
          details = sDetails;
        }
      } catch (e) {
        console.warn('[StandaloneEngine] getMovieDetails error:', e);
      }
    }

    if (thisRequestId !== activeMovieRequestId) return;

    if (!details) {
      details = {
        title: movie.title,
        poster: movie.poster,
        rating: movie.rating,
        year: movie.year,
        synopsis: 'High-speed cloud stream and multi-quality downloads available. Tap Watch Online to stream.',
        qualities: [
          {
            quality: '1080p FHD',
            provider: 'Universal Cinema Cloud',
            downloadUrl: movie.link,
            streamUrl: movie.link,
            type: 'video'
          }
        ]
      };
    } else if (!details.qualities || details.qualities.length === 0) {
      details.qualities = [
        {
          quality: '1080p FHD',
          provider: movie.source || 'Universal Cinema Cloud',
          downloadUrl: movie.link,
          streamUrl: movie.link,
          type: 'video'
        }
      ];
    }

    window.setMovieDetailsDiskCache(movie.link, details, movie.source);

    if (!movieModal || movieModal.classList.contains('hidden')) {
      console.log('[movies.js] Movie modal closed during fetch, skipping UI render for:', movie.title);
      return;
    }

    renderDetailsUi(details, movie);
    if (forceFresh && window.showToast) {
      window.showToast('✅ Live streams refreshed successfully!', 'success');
    }
  } catch (err) {
    if (thisRequestId !== activeMovieRequestId || !movieModal || movieModal.classList.contains('hidden')) {
      return;
    }
    console.error('Error opening movie details:', err);
    renderDetailsUi({
      title: movie.title,
      poster: movie.poster,
      rating: movie.rating,
      year: movie.year,
      synopsis: 'Direct high-speed stream ready. Tap Watch Online.',
      qualities: [
        {
          quality: '1080p FHD',
          provider: movie.source || 'Universal Cinema Stream',
          downloadUrl: movie.link,
          streamUrl: movie.link,
          type: 'video'
        }
      ]
    }, movie);
    if (forceFresh && window.showToast) {
      window.showToast('Failed to refresh fresh links: ' + (err.message || err), 'error');
    }
  }
};

btnCloseMovieModal?.addEventListener('click', window.closeMovieModal);

btnModalRefreshLinks?.addEventListener('click', async (e) => {
  e.stopPropagation();
  const movie = window.state?.selectedMovie;
  if (!movie) return;

  const icon = btnModalRefreshLinks.querySelector('i');
  if (icon) icon.classList.add('fa-spin');

  if (window.showToast) window.showToast('🔄 Fetching live fresh stream links...', 'info');

  try {
    await window.openMovieModal(movie, null, true);
  } catch (err) {
    if (window.showToast) window.showToast('Failed to refresh links: ' + (err.message || err), 'error');
  } finally {
    if (icon) icon.classList.remove('fa-spin');
  }
});

// Startup Trending Movies Feed Loader
function triggerInitialMovieLoad() {
  if (isAppFirstLoaded) return;
  isAppFirstLoaded = true;

  // Free tier optimization: Do not run heavy multi-web scraping on startup if user does not have Movie PRO
  if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('TAB_MOVIES')) {
    return;
  }

  if (window.searchMovies) {
    window.searchMovies('2026');
  }
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', triggerInitialMovieLoad);
} else {
  setTimeout(triggerInitialMovieLoad, 100);
}
