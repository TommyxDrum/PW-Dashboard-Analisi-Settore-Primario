/**
 * Funzioni comuni per tutte le dashboard
 */

// Gestione filtri data (mese/anno)
function initializeDateFilters() {
  const form = document.getElementById('filterForm');
  if (!form) return;

  const monthSel = document.getElementById('monthSel');
  const yearSel = document.getElementById('yearSel');
  const fromEl = document.getElementById('from');
  const toEl = document.getElementById('to');

  function pad(n) {
    return n < 10 ? '0' + n : n.toString();
  }

  function lastDay(y, m) {
    return new Date(y, m, 0).getDate();
  }

  function setRange() {
    const y = parseInt(yearSel.value || new Date().getFullYear(), 10);
    const m = parseInt(monthSel.value || (new Date().getMonth() + 1), 10);
    const d = lastDay(y, m);
    fromEl.value = y + '-' + pad(m) + '-01';
    toEl.value = y + '-' + pad(m) + '-' + pad(d);
  }

  setRange();
  monthSel.addEventListener('change', setRange);
  yearSel.addEventListener('change', setRange);
  form.addEventListener('submit', setRange);
}

// Utility per ottenere variabili CSS
function cssVar(name) {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name);
  return (v && v.trim()) || name;
}

// Colori delle aree
function getAreaColors() {
  return {
    NORD: cssVar('--area-nord'),
    CENTRO: cssVar('--area-centro'),
    SUD: cssVar('--area-sud')
  };
}

// Configurazione default Chart.js
function initializeChartDefaults() {
  if (typeof Chart !== 'undefined') {
    Chart.defaults.font.family = "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif";
    Chart.defaults.font.size = 12;
  }
}

// Formattatori numerici
function getNumberFormatters() {
  return {
    nf0: new Intl.NumberFormat('it-IT', { maximumFractionDigits: 0 }),
    nf2: new Intl.NumberFormat('it-IT', { maximumFractionDigits: 2, minimumFractionDigits: 2 }),
    nfCompact: new Intl.NumberFormat('it-IT', { notation: 'compact', maximumFractionDigits: 1 })
  };
}

// Converti valore a numero sicuro
function toNum(v) {
  if (v == null) return 0;
  if (typeof v === 'number') return isFinite(v) ? v : 0;
  const s = String(v).trim().replace(/\./g, '').replace(',', '.');
  const n = Number(s);
  return isFinite(n) ? n : 0;
}

// Filtra dataset per area selezionata
function filterDatasetsByArea(datasets, areaSel) {
  if (!areaSel || String(areaSel).trim().length === 0) {
    return datasets;
  }
  return datasets.filter(function (d) {
    return d.label.toLowerCase() === String(areaSel).toLowerCase();
  });
}

// Ottieni colore per nome area
function colorByAreaName(name) {
  const colors = getAreaColors();
  const k = String(name).toLowerCase();
  if (k === 'nord') return colors.NORD;
  if (k === 'centro') return colors.CENTRO;
  if (k === 'sud') return colors.SUD;
  return '#999';
}

// Inizializza al caricamento del documento
document.addEventListener('DOMContentLoaded', function () {
  initializeDateFilters();
  initializeChartDefaults();
});