(function () {
  // ====== DATI DA THYMELEAF ======
  const data = window.dashboardData || {};
  const labels = data.labels || [];
  const yNord = data.yieldsNord || [];
  const yCentro = data.yieldsCentro || [];
  const ySud = data.yieldsSud || [];
  const areaSel = data.area || '';

  const effNordKgM3 = data.effNordKgM3;
  const effCentroKgM3 = data.effCentroKgM3;
  const effSudKgM3 = data.effSudKgM3;

  const costNord = data.costNord;
  const costCentro = data.costCentro;
  const costSud = data.costSud;
  const marginNord = data.marginNord;
  const marginCentro = data.marginCentro;
  const marginSud = data.marginSud;

  const riskNord = data.riskNord;
  const riskCentro = data.riskCentro;
  const riskSud = data.riskSud;

  // ====== INIZIALIZZAZIONE DI latestKpiSnapshot ======
  let latestKpiSnapshot = null;

  // ====== COLORI (da common.js) ======
  const colors = (typeof getAreaColors === 'function') ? getAreaColors() : { NORD: '#0d6efd', CENTRO: '#D96D00', SUD: '#10b981' };
  const C_NORD = colors.NORD, C_CENTRO = colors.CENTRO, C_SUD = colors.SUD;
  const colorsAll = [C_NORD, C_CENTRO, C_SUD];

  // ====== RIFERIMENTI GRAFICI ESPORTATI ======
  let effPolarChart = null;
  let echMarginChart = null;
  let echCostChart = null;
  window.effPolarChart = null;
  window.echMarginChart = null;
  window.echCostChart = null;

  // ====== GRAFICO RESA (Chart.js line) ======
  (function buildYieldLine() {
    const el = document.getElementById('yieldMinMax');
    if (!el) return;

    const candidates = [
      { key: 'Nord',   label: 'Nord',   data: yNord,   color: C_NORD },
      { key: 'Centro', label: 'Centro', data: yCentro, color: C_CENTRO },
      { key: 'Sud',    label: 'Sud',    data: ySud,    color: C_SUD }
    ];

    const datasetsPicked = (areaSel && String(areaSel).trim().length > 0)
      ? candidates.filter(d => d.key.toLowerCase() === String(areaSel).toLowerCase())
      : candidates;

    const datasets = datasetsPicked.filter(d => Array.isArray(d.data) && d.data.some(Number.isFinite));
    if (datasets.length === 0) {
      el.parentElement.innerHTML = '<div class="loading"><i class="bi bi-exclamation-circle me-2"></i>Nessun dato disponibile</div>';
      return;
    }

    const allVals = datasets.flatMap(d => d.data).filter(Number.isFinite);
    const yMin0 = Math.min(...allVals);
    const yMax0 = Math.max(...allVals);
    const span = Math.max(0, yMax0 - yMin0);
    const pad = span > 0 ? span * 0.10 : (yMax0 || 1) * 0.10;

    new Chart(el.getContext('2d'), {
      type: 'line',
      data: {
        labels,
        datasets: datasets.map(d => ({
          label: d.label,
          data: d.data,
          borderColor: d.color,
          backgroundColor: d.color + '15',
          pointBackgroundColor: d.color,
          pointBorderColor: '#fff',
          pointBorderWidth: 2,
          pointRadius: 4,
          pointHoverRadius: 6,
          borderWidth: 3,
          tension: 0.35,
          fill: true
        }))
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          y: {
            type: 'linear',
            min: Math.max(0, yMin0 - pad),
            max: yMax0 + pad,
            title: { display: true, text: 'Resa (t/ha)', font: { weight: '600', size: 13 } },
            grid: { color: 'rgba(0,0,0,0.05)' }
          },
          x: { type: 'category', ticks: { autoSkip: true, maxTicksLimit: 12 }, grid: { display: false } }
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            mode: 'index', intersect: false,
            backgroundColor: 'rgba(255,255,255,0.95)',
            titleColor: '#333', bodyColor: '#666',
            borderColor: '#ddd', borderWidth: 1, padding: 12,
            callbacks: { label: (ctx) => `${ctx.dataset.label}: ${(+ctx.raw).toFixed(2)} t/ha` }
          }
        },
        interaction: { mode: 'index', intersect: false }
      }
    });

    const legendContainer = document.getElementById('yieldLegend');
    if (legendContainer) {
      legendContainer.innerHTML =
        `<div class="chart-legend" role="list" aria-label="Legenda aree geografiche">
          ${datasets.map(d => (
            `<span class="item ${d.key.toLowerCase()}" role="listitem">
              <span class="legend-dot"></span><span>${d.label}</span>
            </span>`
          )).join('')}
        </div>`;
    }
  })();

  // ====== GRAFICO EFFICIENZA POLARE (Chart.js polarArea) ======
  (function buildEffPolar() {
    const el = document.getElementById('effPolar');
    if (!el) return;

    // 1) Se esiste già un chart su questo canvas, distruggilo
    const existing = Chart.getChart(el); // funziona da Chart.js v3+
    if (existing) existing.destroy();

    // ... prepara labels, values, colors come già fai ...
    const L = ['Nord', 'Centro', 'Sud'];
    const V = [effNordKgM3, effCentroKgM3, effSudKgM3].map(x => +x);
    const C = colorsAll.slice(0, 3);
    const vmax = Math.max(...V.filter(Number.isFinite), 100);

    // 2) Crea il nuovo chart passando direttamente il canvas (non il context)
    const chart = new Chart(el, {
      type: 'polarArea',
      data: {
        labels: L,
        datasets: [{
          data: V,
          backgroundColor: C.map(c => c.match(/^#([0-9a-f]{6})$/i) ? `${c}40` : c),
          borderColor: C,
          borderWidth: 3
        }]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          r: {
            suggestedMin: 0, suggestedMax: vmax * 1.15,
            ticks: { stepSize: Math.max(5, Math.round(vmax / 5)), callback: v => v + ' Kg/m³' }
          }
        }
      }
    });

    // (opzionale) salva un riferimento sul canvas
    el._chart = chart;

    // Crea legenda HTML come nel grafico Resa
    let legendContainer = document.getElementById('effLegend');

    // Se non esiste, lo crea dinamicamente dopo il canvas
    if (!legendContainer) {
      legendContainer = document.createElement('div');
      legendContainer.id = 'effLegend';
      el.parentElement.appendChild(legendContainer);
    }

    legendContainer.innerHTML =
      `<div class="chart-legend" role="list" aria-label="Legenda aree geografiche">
        ${L.map((label, idx) => (
          `<span class="item ${label.toLowerCase()}" role="listitem">
            <span class="legend-dot" style="background-color: ${C[idx]}"></span><span>${label}</span>
          </span>`
        )).join('')}
      </div>`;
  })();



  // ====== GRAFICI ECHARTS (Margine, Costi) ======
  (function buildECharts() {
    const areasAll = ['Nord', 'Centro', 'Sud'];
    const marginVals = [marginNord, marginCentro, marginSud];
    const costVals = [costNord, costCentro, costSud];

    const sel = (areaSel && String(areaSel).trim()) ? String(areaSel).toLowerCase() : null;
    const idxSel = sel ? ({nord:0, centro:1, sud:2})[sel] ?? -1 : -1;

    const M = (idxSel >= 0) ? { names: [areasAll[idxSel]], values: [marginVals[idxSel]] } : { names: areasAll, values: marginVals };
    const C_DATA = (idxSel >= 0) ? { names: [areasAll[idxSel]], values: [costVals[idxSel]] } : { names: areasAll, values: costVals };

    function buildOption(titleText, cats, vals, defColor) {
      return {
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' },
          backgroundColor: 'rgba(255,255,255,0.95)',
          borderColor: '#ddd',
          borderWidth: 1,
          textStyle: { color: '#333' },
          padding: 12,
          valueFormatter: v => '€ ' + Number(v).toFixed(2) + '/t'
        },
        grid: { left: 50, right: 30, top: 30, bottom: 40, containLabel: true },
        xAxis: {
          type: 'category', data: cats,
          axisTick: { alignWithLabel: true },
          axisLine: { lineStyle: { color: '#ddd' } },
          axisLabel: { color: '#666', fontWeight: '500' }
        },
        yAxis: {
          type: 'value', name: '€/t',
          nameTextStyle: { fontWeight: '600', fontSize: 13 },
          nameGap: 15,
          axisLabel: { formatter: '€ {value}', color: '#666' },
          splitLine: { lineStyle: { color: '#f0f0f0' } }
        },
        series: [{
          type: 'bar',
          name: titleText,
          barWidth: '50%',
          itemStyle: {
            color: cats.length > 1 && typeof colorByAreaName === 'function'
              ? p => colorByAreaName(p.name)
              : defColor,
            borderRadius: [6, 6, 0, 0],
            shadowColor: 'rgba(0,0,0,0.1)',
            shadowBlur: 10,
            shadowOffsetY: 4
          },
          emphasis: { itemStyle: { shadowBlur: 20, shadowOffsetY: 8 } },
          data: vals
        }],
        animationDuration: 800,
        animationEasing: 'cubicOut'
      };
    }

    const mEl = document.getElementById('echMargin');
    const cEl = document.getElementById('echCost');
    if (mEl) {
      echMarginChart = echarts.init(mEl);
      echMarginChart.setOption(buildOption('Margine medio (€/t)', M.names, M.values, '#0dcaf0'));
      window.echMarginChart = echMarginChart;
    }
    if (cEl) {
      echCostChart = echarts.init(cEl);
      echCostChart.setOption(buildOption('Costo medio (€/t)', C_DATA.names, C_DATA.values, '#6f42c1'));
      window.echCostChart = echCostChart;
    }

    window.addEventListener('resize', () => {
      echMarginChart && echMarginChart.resize();
      echCostChart && echCostChart.resize();
    });
  })();

  // ====== RISCHIO CLIMATICO (gauge CSS) ======
  (function buildRiskGauges() {
    const container = document.getElementById('riskGauges');
    if (!container) return;

    const areas = [
      { key: 'Nord', color: '#0d6efd', value: riskNord },
      { key: 'Centro', color: '#D96D00', value: riskCentro },
      { key: 'Sud', color: '#10b981', value: riskSud }
    ];

    let set = areas;
    if (areaSel && String(areaSel).trim()) {
      const a = String(areaSel).toLowerCase();
      set = areas.filter(x => x.key.toLowerCase() === a);
      if (set.length === 0) set = areas;
    }

    function levelBadge(v) {
      if (v > 0.7) return '<span class="badge bg-danger">ALTO</span>';
      if (v >= 0.4) return '<span class="badge" style="background-color:var(--warning-accessible)">MEDIO</span>';
      return '<span class="badge bg-success">BASSO</span>';
    }

    container.innerHTML = set.map(a => {
      const colClass = set.length === 1 ? 'col-12' : (set.length === 2 ? 'col-md-6' : 'col-lg-4');
      return `<div class="${colClass}">
        <div class="p-4 border rounded-3 h-100" style="background: linear-gradient(135deg, #fff 0%, ${a.color}08 100%)">
          <div class="d-flex justify-content-between align-items-center mb-3 pb-2" style="border-bottom:3px solid ${a.color}">
            <h3 class="h6 mb-0 fw-bold" style="color:${a.color}"><i class="bi bi-geo-alt-fill me-2"></i>Area ${a.key}</h3>
            ${levelBadge(a.value)}
          </div>
          <p class="text-muted small mb-3">
            Indice di rischio climatico:
            <span class="fw-bold fs-5" style="color:${a.color}">${(Number(a.value) || 0).toFixed(2)}</span>
            <span class="text-muted">(scala 0-1)</span>
          </p>
          <div class="risk-gauge" role="progressbar"
               aria-label="Indice di rischio climatico ${a.key}"
               aria-valuemin="0" aria-valuemax="1"
               aria-valuenow="${(Number(a.value) || 0).toFixed(2)}"
               style="--risk:${Number.isFinite(a.value) ? a.value : 0}">
            <div class="risk-track"></div>
            <div class="risk-thumb"><span class="risk-value">${(Number(a.value) || 0).toFixed(2)}</span></div>
          </div>
        </div>
      </div>`;
    }).join('');
  })();

  // ====== FUNZIONI DI AGGIORNAMENTO LIVE ======
  function updateKpiCards(dto) {
    latestKpiSnapshot = dto;

    const map = [
      { selector: '[th\\:text*="avgYieldHa"]', value: dto.resaMedia },
      { selector: '[th\\:text*="avgEff"]',     value: dto.efficienzaIdrica },
      { selector: '[th\\:text*="avgCost"]',    value: dto.costoUnitario },
      { selector: '[th\\:text*="avgMargin"]',  value: dto.margineUnitario },
      { selector: '[th\\:text*="avgRisk"]',    value: dto.rischioClimatico }
    ];
    map.forEach(item => {
      const el = document.querySelector(item.selector);
      if (!el || item.value == null) return;
      const oldVal = parseFloat((el.textContent || '0').replace(',', '.')) || 0;
      const newVal = Number(item.value);
      if (Math.abs(oldVal - newVal) > 0.01) {
        el.classList.add('updating');
        animateValue(el, oldVal, newVal, 800);
        setTimeout(() => el.classList.remove('updating'), 800);
      }
    });
  }

  function updateCharts(dto) {
    if (window.echMarginChart && dto.marginePerArea) {
      const areas = Object.keys(dto.marginePerArea);
      const values = Object.values(dto.marginePerArea);
      window.echMarginChart.setOption({ xAxis: { data: areas }, series: [{ data: values }] });
    }
    if (window.echCostChart && dto.costoPerArea) {
      const areas = Object.keys(dto.costoPerArea);
      const values = Object.values(dto.costoPerArea);
      window.echCostChart.setOption({ xAxis: { data: areas }, series: [{ data: values }] });
    }
    if (window.effPolarChart && dto.efficienzaPerArea) {
      const values = Object.values(dto.efficienzaPerArea);
      window.effPolarChart.data.datasets[0].data = values;
      window.effPolarChart.update('none');
    }
    if (dto.rischioPerArea) updateRiskGauges(dto.rischioPerArea);
  }

  function updateRiskGauges(riskMap) {
    Object.keys(riskMap).forEach(area => {
      const value = Number(riskMap[area]);
      const gauge = document.querySelector(`.risk-gauge[data-area="${area}"]`) || null;
      // versioni correnti usano CSS puro; qui potresti aggiungere
      // data-area="{area}" se vuoi aggiornamenti granulari
      // per ora aggiorniamo solo l'aria "visiva" principale:
      if (!gauge) return;
      gauge.style.setProperty('--risk', value);
      const valueSpan = gauge.querySelector('.risk-value');
      if (valueSpan) valueSpan.textContent = value.toFixed(2);
    });
  }

  function animateValue(element, start, end, duration) {
    const range = end - start;
    const increment = range / (duration / 16);
    let current = start;
    const timer = setInterval(() => {
      current += increment;
      if ((increment > 0 && current >= end) || (increment < 0 && current <= end)) {
        current = end; clearInterval(timer);
      }
      element.textContent = current.toFixed(2);
    }, 16);
  }

  function updateLastUpdateTime() {
    const el = document.getElementById('lastUpdateTime');
    if (el) el.textContent = new Date().toLocaleTimeString('it-IT');
  }

  function flashLiveBadge() {
    const badge = document.getElementById('liveBadge');
    if (!badge) return;
    badge.style.animation = 'none';
    setTimeout(() => { badge.style.animation = 'pulse 0.5s ease-in-out'; }, 10);
  }

  // ====== SSE (unica dichiarazione) ======
  let eventSource = null;

  function connectLiveStream() {
    if (eventSource) eventSource.close();
    eventSource = new EventSource('/api/stream/kpi');

    eventSource.addEventListener('kpi-update', (event) => {
      const dto = JSON.parse(event.data);
      updateKpiCards(dto);
      updateCharts(dto);
      updateLastUpdateTime();
      flashLiveBadge();
    });

    eventSource.onerror = () => {
      console.error('❌ Errore streaming, riconnessione in 5s...');
      eventSource.close();
      setTimeout(connectLiveStream, 5000);
    };
  }

  // ====== ALERT POLLING ======
  function startAlertPolling() {
    const poll = () => {
      fetch('/api/alerts/active')
        .then(res => res.json())
        .then(alerts => {
          if (Array.isArray(alerts) && alerts.length) showAlertNotification(alerts);
        })
        .catch(err => console.error('Errore alert:', err));
    };
    poll();
    setInterval(poll, 15000);
  }

  function showAlertNotification(alerts) {
    const container = document.getElementById('alertContainer');
    if (!container) return;

    // Svuoto eventuali alert precedenti
    container.innerHTML = '';

    alerts.forEach(alert => {
        let msg;

        // Caso specifico: alert sulla Resa Media
        if (alert.kpiType === 'Produttività del suolo') {
            // La card Resa ha: <span class="kpi-value text-success">0,00</span>
            // Cerco il primo .kpi-value che è nella card Resa
            let valoreCorrente = null;

            const kpiValues = document.querySelectorAll('.kpi-value');

            if (kpiValues && kpiValues.length > 0) {
              // Il primo .kpi-value è sempre la card Resa
              const text = kpiValues[0].textContent.trim();
              console.log('🔍 Valore dal DOM:', text); // Debug

              // Converte "4,25" (formato italiano) a numero
              // Rimuove il punto (separatore migliaia) e sostituisce virgola con punto
              valoreCorrente = parseFloat(text.replace(/\./g, '').replace(',', '.'));

              console.log('✅ Valore convertito:', valoreCorrente); // Debug
            }

            // Se non trovo il valore nel DOM, prova dal latestKpiSnapshot (fallback)
            if (valoreCorrente === null || isNaN(valoreCorrente)) {
              console.warn('⚠️ Valore non trovato nel DOM, uso fallback'); // Debug

              if (latestKpiSnapshot && typeof latestKpiSnapshot.resaMedia === 'number') {
                valoreCorrente = latestKpiSnapshot.resaMedia;
              } else {
                valoreCorrente = 0;
              }
            }

            const soglia = alert.threshold;

            // Formattiamo in stile italiano con due decimali, come nella card KPI
            const valoreFormattato = valoreCorrente.toLocaleString('it-IT', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            });

            const sogliaFormattata = Number(soglia).toLocaleString('it-IT', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            });

            const verbo = alert.condition === 'ABOVE' ? 'supera' : 'scende sotto';

            msg = `⚠️ Alert:  Produttività del suolo ${valoreFormattato} t/ha ${verbo} soglia ${sogliaFormattata} t/ha`;
        } else {
            // fallback: per altri tipi di alert uso il messaggio pronto del backend
            msg = alert.message || 'Segnalazione';
        }

        // Creo l'alert visivo Bootstrap
        const div = document.createElement('div');
        div.className = 'alert alert-warning alert-dismissible fade show';
        div.innerHTML = `
          ${msg}
          <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        `;
        container.appendChild(div);
    });
  }


  // ====== METEO MINI-WIDGET (SSE dedicato) ======
  function connectMiniWeatherWidget() {
    const src = new EventSource('/api/stream/weather');
    src.addEventListener('weather-update', (event) => {
      const payload = JSON.parse(event.data);
      updateMiniWeatherWidget(payload);
    });
  }

  function updateMiniWeatherWidget(payload) {
    const container = document.getElementById('weatherMiniCards');
    if (!container || !payload || !payload.dataPerArea) return;
    container.innerHTML = '';

    Object.entries(payload.dataPerArea).forEach(([area, w]) => {
      const icon = getWeatherIcon((w && w.condizioni) || '');
      const areaColor =
        area === 'Nord'   ? '#667eea' :
        area === 'Centro' ? '#f5576c' :
        '#00f2fe';

      container.innerHTML += `
        <div class="col-md-4">
          <div class="p-3 border rounded" style="background: linear-gradient(135deg, ${areaColor}15, ${areaColor}05);">
            <div class="d-flex justify-content-between align-items-center">
              <div>
                <strong style="color: ${areaColor}">${area}</strong>
                <div class="small text-muted">${w.condizioni}</div>
              </div>
              <div style="font-size: 2rem;">${icon}</div>
            </div>
            <div class="mt-2 d-flex justify-content-between">
              <span><i class="bi bi-thermometer-half"></i> ${(w.temperaturaC ?? 0).toFixed(1)}°C</span>
              <span><i class="bi bi-droplet-fill"></i> ${(w.umiditaPct ?? 0).toFixed(0)}%</span>
              <span><i class="bi bi-wind"></i> ${(w.ventoKmh ?? 0).toFixed(1)} km/h</span>
            </div>
          </div>
        </div>`;
    });
  }

  function getWeatherIcon(cond) {
    switch (cond) {
      case 'Soleggiato': return '☀️';
      case 'Nuvoloso':   return '☁️';
      case 'Pioggia':    return '🌧️';
      default:           return '🌤️';
    }
  }

  // ====== AGGIORNAMENTO DINAMICO LINK EXPORT ======
  (function () {
      function updateExportLink() {
        const form = document.getElementById('filterForm');
        if (!form) return;

        const params = new URLSearchParams();
        const fromInput = document.getElementById('from');
        const toInput = document.getElementById('to');
        const cropSelect = document.getElementById('cropSel');
        const areaSelect = document.getElementById('areaSel');

        if (fromInput && fromInput.value) params.set('startDate', fromInput.value);
        if (toInput && toInput.value) params.set('endDate', toInput.value);
        if (cropSelect && cropSelect.value) params.set('crop', cropSelect.value);
        if (areaSelect && areaSelect.value) params.set('area', areaSelect.value);

        const exportLink = document.getElementById('exportLink');
        if (exportLink) {
          exportLink.href = '/export' + (params.toString() ? '?' + params.toString() : '');
        }
      }

      // Aggiorna link al caricamento e quando cambiano i filtri
      document.addEventListener('DOMContentLoaded', function () {
        updateExportLink();

        const monthSel = document.getElementById('monthSel');
        const yearSel = document.getElementById('yearSel');
        const cropSel = document.getElementById('cropSel');
        const areaSel = document.getElementById('areaSel');

        if (monthSel) monthSel.addEventListener('change', updateExportLink);
        if (yearSel) yearSel.addEventListener('change', updateExportLink);
        if (cropSel) cropSel.addEventListener('change', updateExportLink);
        if (areaSel) areaSel.addEventListener('change', updateExportLink);
      });
    })();

  // ====== LIFECYCLE ======
  document.addEventListener('DOMContentLoaded', () => {
    connectLiveStream();
    connectMiniWeatherWidget();
    startAlertPolling();
  });

  window.addEventListener('beforeunload', () => { if (eventSource) eventSource.close(); });
})();