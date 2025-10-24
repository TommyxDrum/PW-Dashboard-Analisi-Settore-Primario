(function () {
  const colors = getAreaColors();
  const C_NORD   = colors.NORD;
  const C_CENTRO = colors.CENTRO;
  const C_SUD    = colors.SUD;

  const years  = window.rischioData.years;
  const annualRiskNord   = window.rischioData.annualRiskNord;
  const annualRiskCentro = window.rischioData.annualRiskCentro;
  const annualRiskSud    = window.rischioData.annualRiskSud;
  const areaSel = window.rischioData.area;

  const riskTempNord   = window.rischioData.riskTempNord;
  const riskTempCentro = window.rischioData.riskTempCentro;
  const riskTempSud    = window.rischioData.riskTempSud;
  const riskWaterNord  = window.rischioData.riskWaterNord;
  const riskWaterCentro= window.rischioData.riskWaterCentro;
  const riskWaterSud   = window.rischioData.riskWaterSud;
  const riskFrostNord  = window.rischioData.riskFrostNord;
  const riskFrostCentro= window.rischioData.riskFrostCentro;
  const riskFrostSud   = window.rischioData.riskFrostSud;

  // Serie giornaliera per il grafico mensile del rischio climatico
  const dailyLabels     = window.rischioData.dailyLabels;
  const dailyRiskNord   = window.rischioData.dailyRiskNord;
  const dailyRiskCentro = window.rischioData.dailyRiskCentro;
  const dailyRiskSud    = window.rischioData.dailyRiskSud;

  /* ===== Grafico Andamento Rischio Climatico Annuale ===== */
  (function () {
    const el = document.getElementById('riskAnnualTrend');
    if (!el) return;
    if (!years || years.length === 0) {
      el.parentElement.innerHTML =
        '<div class="loading"><i class="bi bi-exclamation-circle me-2"></i>Nessun dato storico disponibile</div>';
      return;
    }
    const datasets = [
      { label: 'Nord',   data: annualRiskNord,   borderColor: C_NORD,   backgroundColor: C_NORD + '15' },
      { label: 'Centro', data: annualRiskCentro, borderColor: C_CENTRO, backgroundColor: C_CENTRO + '15' },
      { label: 'Sud',    data: annualRiskSud,    borderColor: C_SUD,    backgroundColor: C_SUD + '15' }
    ];
    const filtered = filterDatasetsByArea(datasets, areaSel);

    new Chart(el.getContext('2d'), {
      type: 'line',
      data: {
        labels: years,
        datasets: filtered.map(function (d) {
          return {
            label: d.label,
            data:  d.data,
            borderColor: d.borderColor,
            backgroundColor: d.backgroundColor,
            pointBackgroundColor: d.borderColor,
            pointBorderColor: '#fff',
            pointBorderWidth: 2,
            pointRadius: 4,
            pointHoverRadius: 6,
            borderWidth: 3,
            tension: 0.35,
            fill: true
          };
        })
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          y: {
            min: 0,
            max: 1,
            title: { display: true, text: 'Indice di Rischio (0-1)', font: { weight: '600', size: 13 } },
            grid: { color: 'rgba(0,0,0,0.05)' }
          },
          x: {
            ticks: { autoSkip: true, maxTicksLimit: 10 },
            grid: { display: false }
          }
        },
        plugins: {
          legend: {
            position: 'top',
            labels: { usePointStyle: true, padding: 15, font: { weight: '500' } }
          },
          tooltip: {
            mode: 'index',
            intersect: false,
            backgroundColor: 'rgba(255,255,255,0.95)',
            titleColor: '#333',
            bodyColor: '#666',
            borderColor: '#ddd',
            borderWidth: 1,
            padding: 12,
            callbacks: {
              label: function (ctx) {
                return ctx.dataset.label + ': ' + (+ctx.raw).toFixed(2);
              }
            }
          }
        },
        interaction: { mode: 'index', intersect: false }
      }
    });
  })();

  /* ===== Line Chart: Andamento Rischio Climatico Mensile ===== */
  (function () {
    const el = document.getElementById('riskMonthlyTrend');
    if (!el) return;
    if (!dailyLabels || dailyLabels.length === 0) {
      el.parentElement.innerHTML =
        '<div class="loading"><i class="bi bi-exclamation-circle me-2"></i>Dati non disponibili per il periodo selezionato</div>';
      return;
    }
    const datasets = [
      { label: 'Nord',   data: dailyRiskNord,   borderColor: C_NORD,   backgroundColor: C_NORD + '15' },
      { label: 'Centro', data: dailyRiskCentro, borderColor: C_CENTRO, backgroundColor: C_CENTRO + '15' },
      { label: 'Sud',    data: dailyRiskSud,    borderColor: C_SUD,    backgroundColor: C_SUD + '15' }
    ];
    const filtered = filterDatasetsByArea(datasets, areaSel);
    // Calcola min/max con margine e clamp nel range [0,1]
    const allVals = filtered.flatMap(function (d) {
      return Array.isArray(d.data) ? d.data : [];
    }).filter(function (v) {
      return typeof v === 'number' && !isNaN(v);
    });
    let yMin0 = 0;
    let yMax0 = 0;
    if (allVals.length > 0) {
      yMin0 = Math.min.apply(null, allVals);
      yMax0 = Math.max.apply(null, allVals);
    }
    const span = Math.max(0, yMax0 - yMin0);
    const pad  = span > 0 ? span * 0.10 : 0.05;
    const yMin = Math.max(0, yMin0 - pad);
    const yMax = Math.min(1, yMax0 + pad);
    const ctx  = el.getContext('2d');
    const chart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: dailyLabels,
        datasets: filtered.map(function (d) {
          return {
            label: d.label,
            data:  d.data,
            borderColor: d.borderColor,
            backgroundColor: d.backgroundColor,
            pointBackgroundColor: d.borderColor,
            pointBorderColor: '#fff',
            pointBorderWidth: 2,
            pointRadius: 4,
            pointHoverRadius: 6,
            borderWidth: 3,
            tension: 0.35,
            fill: true
          };
        })
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          y: {
            min: yMin,
            max: yMax,
            title: { display: true, text: 'Indice di Rischio (0-1)', font: { weight: '600', size: 13 } },
            grid: { color: 'rgba(0,0,0,0.05)' }
          },
          x: {
            ticks: { autoSkip: true, maxTicksLimit: 15 },
            grid: { display: false }
          }
        },
        plugins: {
          legend: {
            position: 'top',
            labels: { usePointStyle: true, padding: 15, font: { weight: '500' } }
          },
          tooltip: {
            mode: 'index',
            intersect: false,
            backgroundColor: 'rgba(255,255,255,0.95)',
            titleColor: '#333',
            bodyColor: '#666',
            borderColor: '#ddd',
            borderWidth: 1,
            padding: 12,
            callbacks: {
              label: function (ctx) {
                return ctx.dataset.label + ': ' + (+ctx.raw).toFixed(2);
              }
            }
          }
        },
        interaction: { mode: 'index', intersect: false }
      }
    });
    // Espone riferimento globale per eventuali export o debug
    window.riskMonthlyTrendChart = chart;
  })();

  /* ===== ECharts: Radar Profilo Rischio ===== */
  (function () {
    const el = document.getElementById('echRadarRiskProfile');
    if (!el) return;
    const chart = echarts.init(el);

    const data = [
      {
        name: 'Nord',
        value: [riskTempNord, riskWaterNord, riskFrostNord],
        lineStyle: { color: C_NORD, width: 2 },
        areaStyle: { color: C_NORD + '40' }
      },
      {
        name: 'Centro',
        value: [riskTempCentro, riskWaterCentro, riskFrostCentro],
        lineStyle: { color: C_CENTRO, width: 2 },
        areaStyle: { color: C_CENTRO + '40' }
      },
      {
        name: 'Sud',
        value: [riskTempSud, riskWaterSud, riskFrostSud],
        lineStyle: { color: C_SUD, width: 2 },
        areaStyle: { color: C_SUD + '40' }
      }
    ];
    let filteredData = data;
    if (areaSel && String(areaSel).trim().length > 0) {
      filteredData = data.filter(function (d) {
        return d.name.toLowerCase() === String(areaSel).toLowerCase();
      });
    }

    if (filteredData.every(function (d) {
      return d.value.every(function (v) { return v === 0; });
    })) {
      el.innerHTML =
        '<div class="loading"><i class="bi bi-exclamation-circle me-2"></i>Dati di rischio non disponibili</div>';
      return;
    }

    chart.setOption({
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(255,255,255,0.95)',
        borderColor: '#ddd',
        borderWidth: 1,
        textStyle: { color: '#333' },
        padding: 12,
        formatter: function (params) {
          const indicators = ['Rischio Termico', 'Stress Idrico', 'Rischio Gelo'];
          let result = '<b>' + params.name + '</b><br/>';
          for (let i = 0; i < params.value.length; i++) {
            result += indicators[i] + ': ' + (params.value[i] || 0).toFixed(2) + '<br/>';
          }
          return result;
        }
      },
      legend: {
        data: filteredData.map(function (d) { return d.name; }),
        bottom: 0,
        textStyle: { fontWeight: '500' }
      },
      radar: {
        indicator: [
          { name: 'Rischio\nTermico', max: 1 },
          { name: 'Stress\nIdrico',   max: 1 },
          { name: 'Rischio\nGelo',    max: 1 }
        ],
        shape: 'circle',
        center: ['50%', '45%'],
        radius: '60%',
        splitNumber: 5,
        axisName: {
          color: '#666',
          fontWeight: '500',
          fontSize: 12
        },
        splitLine: {
          lineStyle: { color: '#e0e0e0' }
        },
        splitArea: {
          show: true,
          areaStyle: {
            color: ['rgba(255,255,255,0.1)', 'rgba(0,0,0,0.02)']
          }
        },
        axisLine: {
          lineStyle: { color: '#ccc' }
        }
      },
      series: [{
        type: 'radar',
        data: filteredData,
        emphasis: {
          lineStyle: { width: 3 }
        }
      }],
      animationDuration: 800,
      animationEasing: 'cubicOut'
    });

    window.addEventListener('resize', function () {
      chart.resize();
    });

    // Gestione dei filtri e export: data range e parametri URL
    (function() {
      function pad(n) { return n < 10 ? '0' + n : n.toString(); }

      function updateExportLink() {
        const form  = document.getElementById('filterForm');
        if (!form) return;

        const params = new URLSearchParams();
        const fromInput  = document.getElementById('from');
        const toInput    = document.getElementById('to');
        const cropSelect = document.getElementById('cropSel');
        const areaSelect = document.getElementById('areaSel');

        if (fromInput && fromInput.value) params.set('startDate', fromInput.value);
        if (toInput && toInput.value)     params.set('endDate',   toInput.value);
        if (cropSelect && cropSelect.value) params.set('crop', cropSelect.value);
        if (areaSelect && areaSelect.value) params.set('area', areaSelect.value);

        const exportLink = document.getElementById('exportLink');
        if (exportLink) {
          exportLink.href = '/export' + (params.toString() ? '?' + params.toString() : '');
        }
      }

      function updateDateRange() {
        const yearSel  = document.getElementById('yearSel');
        const monthSel = document.getElementById('monthSel');
        const fromInput= document.getElementById('from');
        const toInput  = document.getElementById('to');

        if (!yearSel || !monthSel || !fromInput || !toInput) return;

        const year  = parseInt(yearSel.value, 10);
        const month = parseInt(monthSel.value, 10);
        const lastDay = new Date(year, month, 0).getDate();

        fromInput.value = year + '-' + pad(month) + '-01';
        toInput.value   = year + '-' + pad(month) + '-' + pad(lastDay);

        updateExportLink();
      }

      document.addEventListener('DOMContentLoaded', function() {
        updateDateRange();
        updateExportLink();

        const monthSel = document.getElementById('monthSel');
        const yearSel  = document.getElementById('yearSel');
        const cropSel  = document.getElementById('cropSel');
        const areaSel  = document.getElementById('areaSel');
        const form     = document.getElementById('filterForm');

        if (monthSel) monthSel.addEventListener('change', updateDateRange);
        if (yearSel)  yearSel.addEventListener('change', updateDateRange);
        if (cropSel)  cropSel.addEventListener('change', updateExportLink);
        if (areaSel)  areaSel.addEventListener('change', updateExportLink);
        if (form)     form.addEventListener('submit', updateDateRange);
      });
    })();

    // Espone riferimento globale per il radar del rischio
    window.echRadarRiskProfileChart = chart;
  })();
})();
