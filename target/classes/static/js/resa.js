(function () {
  const colors = getAreaColors();
  const C_NORD = colors.NORD;
  const C_CENTRO = colors.CENTRO;
  const C_SUD = colors.SUD;

  const formatters = getNumberFormatters();

  const years = window.resaData.years;
  const annualResaNord = window.resaData.annualResaNord;
  const annualResaCentro = window.resaData.annualResaCentro;
  const annualResaSud = window.resaData.annualResaSud;
  const areaSel = window.resaData.area;

  const yieldNordT = window.resaData.yieldNordT;
  const yieldCentroT = window.resaData.yieldCentroT;
  const yieldSudT = window.resaData.yieldSudT;
  const surfNordHa = window.resaData.surfNordHa;
  const surfCentroHa = window.resaData.surfCentroHa;
  const surfSudHa = window.resaData.surfSudHa;
  const totalYieldT = window.resaData.totalYieldT;
  const totalSurfaceHa = window.resaData.totalSurfaceHa;
  const totalResa = window.resaData.totalResa;

  /* Popolamento Card KPI */
  (function () {
    document.getElementById('totalValueProd').textContent = formatters.nfCompact.format(toNum(totalYieldT));
    document.getElementById('totalValueSurf').textContent = formatters.nfCompact.format(toNum(totalSurfaceHa));
    document.getElementById('totalValueResa').textContent = formatters.nf2.format(toNum(totalResa));
  })();

  /* ===== Line Chart: Andamento Annuale Resa ===== */
  (function () {
    const el = document.getElementById('resaAnnualTrend');
    if (!el) return;
    if (!years || years.length === 0) {
      el.parentElement.innerHTML = '<div class="loading"><i class="bi bi-exclamation-circle me-2"></i>Nessun dato storico disponibile</div>';
      return;
    }
    const datasets = [
      { label: 'Nord', data: annualResaNord, borderColor: C_NORD, backgroundColor: C_NORD + '15' },
      { label: 'Centro', data: annualResaCentro, borderColor: C_CENTRO, backgroundColor: C_CENTRO + '15' },
      { label: 'Sud', data: annualResaSud, borderColor: C_SUD, backgroundColor: C_SUD + '15' }
    ];
    const filtered = filterDatasetsByArea(datasets, areaSel);

    const resaChart = new Chart(el.getContext('2d'), {
      type: 'line',
      data: {
        labels: years,
        datasets: filtered.map(function (d) {
          return {
            label: d.label,
            data: d.data,
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
            title: { display: true, text: 'Resa (t/ha)', font: { weight: '600', size: 13 } },
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
                return ctx.dataset.label + ': ' + (+ctx.raw).toFixed(2) + ' t/ha';
              }
            }
          }
        },
        interaction: { mode: 'index', intersect: false }
      }
    });

    // 🔴 ESPORTA riferimento globale
    window.resaAnnualTrendChart = resaChart;
  })();

  /* ===== Donut Chart: Composizione Produzione ===== */
  (function () {
    const donutEl = document.getElementById('echDonutYield');
    if (!donutEl) return;
    const donutChart = echarts.init(donutEl);

    const data = [
      { value: toNum(yieldNordT), name: 'Nord', itemStyle: { color: C_NORD } },
      { value: toNum(yieldCentroT), name: 'Centro', itemStyle: { color: C_CENTRO } },
      { value: toNum(yieldSudT), name: 'Sud', itemStyle: { color: C_SUD } }
    ].filter(function (d) { return d.value > 0; });

    if (data.length === 0) {
      donutEl.innerHTML = '<div class="loading"><i class="bi bi-exclamation-circle me-2"></i>Nessun dato di produzione disponibile</div>';
      return;
    }

    donutChart.setOption({
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(255,255,255,0.95)',
        borderColor: '#ddd',
        borderWidth: 1,
        textStyle: { color: '#333' },
        padding: 12,
        formatter: function (p) {
          return '<b>' + p.name + '</b><br/>Produzione: ' + formatters.nf0.format(p.value) + ' t (' + p.percent + '%)';
        }
      },
      legend: { show: false },
      series: [{
        name: 'Produzione',
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: true,
        label: {
          show: true,
          position: 'outside',
          formatter: '{b}\n{d}%',
          fontSize: 14,
          color: '#495057',
          fontWeight: '500'
        },
        labelLine: { show: true, length: 15, length2: 15 },
        emphasis: {
          label: { show: true, fontSize: 16, fontWeight: 'bold' },
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(0,0,0,0.3)'
          }
        },
        data: data
      }],
      animationDuration: 800,
      animationEasing: 'cubicOut'
    });

    window.addEventListener('resize', function () {
      donutChart.resize();
    });

    (function() {
          function pad(n) { return n < 10 ? '0' + n : n.toString(); }

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

          function updateDateRange() {
            const yearSel = document.getElementById('yearSel');
            const monthSel = document.getElementById('monthSel');
            const fromInput = document.getElementById('from');
            const toInput = document.getElementById('to');

            if (!yearSel || !monthSel || !fromInput || !toInput) return;

            const year = parseInt(yearSel.value, 10);
            const month = parseInt(monthSel.value, 10);
            const lastDay = new Date(year, month, 0).getDate();

            fromInput.value = year + '-' + pad(month) + '-01';
            toInput.value = year + '-' + pad(month) + '-' + pad(lastDay);

            updateExportLink();
          }

          document.addEventListener('DOMContentLoaded', function() {
            updateDateRange();
            updateExportLink();

            const monthSel = document.getElementById('monthSel');
            const yearSel = document.getElementById('yearSel');
            const cropSel = document.getElementById('cropSel');
            const areaSel = document.getElementById('areaSel');
            const form = document.getElementById('filterForm');

            if (monthSel) monthSel.addEventListener('change', updateDateRange);
            if (yearSel) yearSel.addEventListener('change', updateDateRange);
            if (cropSel) cropSel.addEventListener('change', updateExportLink);
            if (areaSel) areaSel.addEventListener('change', updateExportLink);
            if (form) form.addEventListener('submit', updateDateRange);
          });
        })();

    // ESPORTA riferimento globale
    window.echDonutYieldChart = donutChart;
  })();
})();