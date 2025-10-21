(function () {
  const colors = getAreaColors();
  const C_NORD = colors.NORD;
  const C_CENTRO = colors.CENTRO;
  const C_SUD = colors.SUD;

  const years = window.efficienzaData.years;
  const annualEfficiencyNord = window.efficienzaData.annualEfficiencyNord;
  const annualEfficiencyCentro = window.efficienzaData.annualEfficiencyCentro;
  const annualEfficiencySud = window.efficienzaData.annualEfficiencySud;
  const areaSel = window.efficienzaData.area;

  const waterNordM3 = window.efficienzaData.waterNordM3;
  const waterCentroM3 = window.efficienzaData.waterCentroM3;
  const waterSudM3 = window.efficienzaData.waterSudM3;
  const yieldNordT = window.efficienzaData.yieldNordT;
  const yieldCentroT = window.efficienzaData.yieldCentroT;
  const yieldSudT = window.efficienzaData.yieldSudT;

  const formatters = getNumberFormatters();

  /* ===== Grafico Andamento Efficienza Idrica Annuale ===== */
  (function () {
    const el = document.getElementById('efficiencyAnnualTrend');
    if (!el) return;

    if (!Array.isArray(years) || years.length === 0) {
      el.parentElement.innerHTML = '<div class="loading"><i class="bi bi-exclamation-circle me-2"></i>Nessun dato storico disponibile</div>';
      return;
    }

    const datasets = [
      { label: 'Nord', data: annualEfficiencyNord, borderColor: C_NORD, backgroundColor: C_NORD + '15' },
      { label: 'Centro', data: annualEfficiencyCentro, borderColor: C_CENTRO, backgroundColor: C_CENTRO + '15' },
      { label: 'Sud', data: annualEfficiencySud, borderColor: C_SUD, backgroundColor: C_SUD + '15' }
    ];

    const filtered = filterDatasetsByArea(datasets, areaSel);

    const effChart = new Chart(el.getContext('2d'), {
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
            title: { display: true, text: 'Efficienza (Kg/m³)', font: { weight: '600', size: 13 } },
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
                return ctx.dataset.label + ': ' + (+ctx.raw).toFixed(2) + ' Kg/m³';
              }
            }
          }
        },
        interaction: { mode: 'index', intersect: false }
      }
    });

    // 🔴 ESPORTA riferimento globale
    window.efficiencyAnnualTrendChart = effChart;
  })();

  /* ===== ECharts: Scatter Produzione vs Consumo Idrico ===== */
  (function () {
    const el = document.getElementById('echScatterEfficiency');
    if (!el) return;

    const chart = echarts.init(el);

    const data = [
      {
        name: 'Nord',
        value: [Number(waterNordM3) || 0, Number(yieldNordT) || 0],
        itemStyle: { color: C_NORD }
      },
      {
        name: 'Centro',
        value: [Number(waterCentroM3) || 0, Number(yieldCentroT) || 0],
        itemStyle: { color: C_CENTRO }
      },
      {
        name: 'Sud',
        value: [Number(waterSudM3) || 0, Number(yieldSudT) || 0],
        itemStyle: { color: C_SUD }
      }
    ];

    if (data.every(function (d) { return d.value[0] === 0 && d.value[1] === 0; })) {
      el.innerHTML = '<div class="loading"><i class="bi bi-exclamation-circle me-2"></i>Dati non sufficienti per il confronto</div>';
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
        formatter: function (p) {
          const efficiency = p.data.value[1] > 0 && p.data.value[0] > 0
            ? (p.data.value[1] * 1000 / p.data.value[0]).toFixed(2)
            : '0.00';
          return '<b>' + p.data.name + '</b><br/>' +
            'Consumo: ' + formatters.nf0.format(p.data.value[0]) + ' m³<br/>' +
            'Produzione: ' + formatters.nf0.format(p.data.value[1]) + ' t<br/>' +
            'Efficienza: ' + efficiency + ' Kg/m³';
        }
      },
      grid: {
        left: '12%',
        right: '8%',
        bottom: '12%',
        top: '8%',
        containLabel: true
      },
      xAxis: {
        type: 'value',
        name: 'Consumo Idrico (m³)',
        nameLocation: 'middle',
        nameGap: 35,
        nameTextStyle: { fontWeight: '600', fontSize: 13 },
        axisLabel: { color: '#666' },
        splitLine: { lineStyle: { color: '#f0f0f0' } }
      },
      yAxis: {
        type: 'value',
        name: 'Produzione (t)',
        nameLocation: 'middle',
        nameGap: 50,
        nameTextStyle: { fontWeight: '600', fontSize: 13 },
        axisLabel: { color: '#666' },
        splitLine: { lineStyle: { color: '#f0f0f0' } }
      },
      series: [{
        type: 'scatter',
        symbolSize: 24,
        data: data,
        emphasis: {
          focus: 'series',
          label: {
            show: true,
            formatter: function (p) { return p.data.name; },
            position: 'top',
            fontWeight: '600',
            fontSize: 13
          },
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(0,0,0,0.3)'
          }
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