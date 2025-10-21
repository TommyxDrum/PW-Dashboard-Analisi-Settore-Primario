(function () {
  const colors = getAreaColors();
  const C_NORD = colors.NORD;
  const C_CENTRO = colors.CENTRO;
  const C_SUD = colors.SUD;
  const C_LABOR = cssVar('--kpi-labor-border');
  const C_MATERIALS = cssVar('--kpi-materials-border');

  const years = window.costiData.years;
  const annualCostNord = window.costiData.annualCostNord;
  const annualCostCentro = window.costiData.annualCostCentro;
  const annualCostSud = window.costiData.annualCostSud;
  const areaSel = window.costiData.area;

  const avgCostLaborNord = window.costiData.avgCostLaborNord;
  const avgCostLaborCentro = window.costiData.avgCostLaborCentro;
  const avgCostLaborSud = window.costiData.avgCostLaborSud;
  const avgCostMaterialsNord = window.costiData.avgCostMaterialsNord;
  const avgCostMaterialsCentro = window.costiData.avgCostMaterialsCentro;
  const avgCostMaterialsSud = window.costiData.avgCostMaterialsSud;

  /* ===== Grafico Andamento Annuale Costi ===== */
  (function () {
    const el = document.getElementById('costAnnualTrend');
    if (!el) return;
    if (!years || years.length === 0) {
      el.parentElement.innerHTML = '<div class="loading"><i class="bi bi-exclamation-circle me-2"></i>Nessun dato storico disponibile</div>';
      return;
    }
    const datasets = [
      { label: 'Nord', data: annualCostNord, borderColor: C_NORD, backgroundColor: C_NORD + '15' },
      { label: 'Centro', data: annualCostCentro, borderColor: C_CENTRO, backgroundColor: C_CENTRO + '15' },
      { label: 'Sud', data: annualCostSud, borderColor: C_SUD, backgroundColor: C_SUD + '15' }
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
            title: { display: true, text: 'Costo (€/t)', font: { weight: '600', size: 13 } },
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
                return ctx.dataset.label + ': ' + (+ctx.raw).toFixed(2) + ' €/t';
              }
            }
          }
        },
        interaction: { mode: 'index', intersect: false }
      }
    });
  })();

  /* ===== ECharts: Scomposizione Costi ===== */
  (function () {
    const el = document.getElementById('echBarCostComposition');
    if (!el) return;
    const chart = echarts.init(el);
    const nf2 = new Intl.NumberFormat('it-IT', { maximumFractionDigits: 2, minimumFractionDigits: 2 });
    const areas = ['Nord', 'Centro', 'Sud'];

    const dataLabor = [
      Number(avgCostLaborNord) || 0,
      Number(avgCostLaborCentro) || 0,
      Number(avgCostLaborSud) || 0
    ];
    const dataMaterials = [
      Number(avgCostMaterialsNord) || 0,
      Number(avgCostMaterialsCentro) || 0,
      Number(avgCostMaterialsSud) || 0
    ];

    if (dataLabor.every(function (v) { return v === 0; }) && dataMaterials.every(function (v) { return v === 0; })) {
      el.innerHTML = '<div class="loading"><i class="bi bi-exclamation-circle me-2"></i>Dati sui costi non disponibili</div>';
      return;
    }

    chart.setOption({
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        backgroundColor: 'rgba(255,255,255,0.95)',
        borderColor: '#ddd',
        borderWidth: 1,
        textStyle: { color: '#333' },
        padding: 12,
        formatter: function (params) {
          let total = 0;
          let tooltipStr = '<b>' + params[0].axisValue + '</b><br/>';
          for (let i = 0; i < params.length; i++) {
            total += params[i].value || 0;
            tooltipStr += '<span style="color:' + params[i].color + '">●</span> ' +
              params[i].seriesName + ': ' + nf2.format(params[i].value) + ' €/t<br/>';
          }
          tooltipStr += '<b>Costo Totale: ' + nf2.format(total) + ' €/t</b>';
          return tooltipStr;
        }
      },
      legend: {
        data: ['Manodopera', 'Materiali'],
        bottom: 0,
        textStyle: { fontWeight: '500' }
      },
      grid: { left: '12%', right: '8%', bottom: '15%', top: '10%' },
      xAxis: {
        type: 'category',
        data: areas,
        axisTick: { alignWithLabel: true },
        axisLine: { lineStyle: { color: '#ddd' } },
        axisLabel: { color: '#666', fontWeight: '500' }
      },
      yAxis: {
        type: 'value',
        name: '€/t',
        nameTextStyle: { fontWeight: '600', fontSize: 13 },
        nameGap: 15,
        axisLabel: {
          formatter: '€ {value}',
          color: '#666'
        },
        splitLine: { lineStyle: { color: '#f0f0f0' } }
      },
      series: [
        {
          name: 'Manodopera',
          type: 'bar',
          stack: 'total',
          barWidth: '50%',
          label: { show: false },
          emphasis: {
            focus: 'series',
            itemStyle: {
              shadowBlur: 20,
              shadowOffsetY: 8
            }
          },
          itemStyle: {
            color: C_LABOR,
            borderRadius: [0, 0, 0, 0],
            shadowColor: 'rgba(0,0,0,0.1)',
            shadowBlur: 10,
            shadowOffsetY: 4
          },
          data: dataLabor
        },
        {
          name: 'Materiali',
          type: 'bar',
          stack: 'total',
          label: { show: false },
          emphasis: {
            focus: 'series',
            itemStyle: {
              shadowBlur: 20,
              shadowOffsetY: 8
            }
          },
          itemStyle: {
            color: C_MATERIALS,
            borderRadius: [6, 6, 0, 0],
            shadowColor: 'rgba(0,0,0,0.1)',
            shadowBlur: 10,
            shadowOffsetY: 4
          },
          data: dataMaterials
        }
      ],
      animationDuration: 800,
      animationEasing: 'cubicOut'
    });

    window.addEventListener('resize', function () {
      chart.resize();
    });
  })();
})();