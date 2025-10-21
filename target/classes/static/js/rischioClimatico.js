(function () {
  const colors = getAreaColors();
  const C_NORD = colors.NORD;
  const C_CENTRO = colors.CENTRO;
  const C_SUD = colors.SUD;

  const years = window.rischioData.years;
  const annualRiskNord = window.rischioData.annualRiskNord;
  const annualRiskCentro = window.rischioData.annualRiskCentro;
  const annualRiskSud = window.rischioData.annualRiskSud;
  const areaSel = window.rischioData.area;

  const riskTempNord = window.rischioData.riskTempNord;
  const riskTempCentro = window.rischioData.riskTempCentro;
  const riskTempSud = window.rischioData.riskTempSud;
  const riskWaterNord = window.rischioData.riskWaterNord;
  const riskWaterCentro = window.rischioData.riskWaterCentro;
  const riskWaterSud = window.rischioData.riskWaterSud;
  const riskFrostNord = window.rischioData.riskFrostNord;
  const riskFrostCentro = window.rischioData.riskFrostCentro;
  const riskFrostSud = window.rischioData.riskFrostSud;

  /* ===== Grafico Andamento Storico Rischio Climatico ===== */
  (function () {
    const el = document.getElementById('riskAnnualTrend');
    if (!el) return;
    if (!years || years.length === 0) {
      el.parentElement.innerHTML = '<div class="loading"><i class="bi bi-exclamation-circle me-2"></i>Nessun dato storico disponibile</div>';
      return;
    }
    const datasets = [
      { label: 'Nord', data: annualRiskNord, borderColor: C_NORD, backgroundColor: C_NORD + '15' },
      { label: 'Centro', data: annualRiskCentro, borderColor: C_CENTRO, backgroundColor: C_CENTRO + '15' },
      { label: 'Sud', data: annualRiskSud, borderColor: C_SUD, backgroundColor: C_SUD + '15' }
    ];
    const filtered = filterDatasetsByArea(datasets, areaSel);

    new Chart(el.getContext('2d'), {
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
      el.innerHTML = '<div class="loading"><i class="bi bi-exclamation-circle me-2"></i>Dati di rischio non disponibili</div>';
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
          { name: 'Stress\nIdrico', max: 1 },
          { name: 'Rischio\nGelo', max: 1 }
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

    // 🔴 ESPORTA riferimento globale
    window.echScatterEfficiencyChart = chart;
  })();
})();