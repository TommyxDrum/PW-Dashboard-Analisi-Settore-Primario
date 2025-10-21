(function () {
  const labels = window.dashboardData.labels;
  const yNord = window.dashboardData.yieldsNord;
  const yCentro = window.dashboardData.yieldsCentro;
  const ySud = window.dashboardData.yieldsSud;
  const areaSel = window.dashboardData.area;

  const avgEff = window.dashboardData.avgEff;
  const effMax = window.dashboardData.effMaxScale;
  const avgCost = window.dashboardData.avgCost;
  const avgMargin = window.dashboardData.avgMargin;

  const effNordKgM3 = window.dashboardData.effNordKgM3;
  const effCentroKgM3 = window.dashboardData.effCentroKgM3;
  const effSudKgM3 = window.dashboardData.effSudKgM3;

  const costNord = window.dashboardData.costNord;
  const costCentro = window.dashboardData.costCentro;
  const costSud = window.dashboardData.costSud;
  const marginNord = window.dashboardData.marginNord;
  const marginCentro = window.dashboardData.marginCentro;
  const marginSud = window.dashboardData.marginSud;

  const riskNord = window.dashboardData.riskNord;
  const riskCentro = window.dashboardData.riskCentro;
  const riskSud = window.dashboardData.riskSud;

  const colors = getAreaColors();
  const C_NORD = colors.NORD;
  const C_CENTRO = colors.CENTRO;
  const C_SUD = colors.SUD;
  const colorsAll = [C_NORD, C_CENTRO, C_SUD];

  /* ===== Resa: Linear Scale Min/Max ===== */
  (function () {
    const el = document.getElementById('yieldMinMax');
    if (!el) return;

    const allCandidates = [
      { key: 'Nord', label: 'Nord', data: yNord, color: C_NORD },
      { key: 'Centro', label: 'Centro', data: yCentro, color: C_CENTRO },
      { key: 'Sud', label: 'Sud', data: ySud, color: C_SUD }
    ];

    const datasetsPicked = (areaSel && String(areaSel).trim().length > 0)
      ? allCandidates.filter(function (d) { return d.key.toLowerCase() === String(areaSel).toLowerCase(); })
      : allCandidates;

    const datasetsNonEmpty = datasetsPicked.filter(function (d) {
      return Array.isArray(d.data) && d.data.some(function (v) { return Number.isFinite(v); });
    });

    if (datasetsNonEmpty.length === 0) {
      el.parentElement.innerHTML = '<div class="loading"><i class="bi bi-exclamation-circle me-2"></i>Nessun dato disponibile</div>';
      return;
    }

    const allVals = datasetsNonEmpty.flatMap(function (d) { return d.data; }).filter(Number.isFinite);
    const yMin0 = Math.min.apply(null, allVals);
    const yMax0 = Math.max.apply(null, allVals);
    const span = Math.max(0, yMax0 - yMin0);
    const pad = span > 0 ? span * 0.10 : (yMax0 || 1) * 0.10;

    new Chart(el.getContext('2d'), {
      type: 'line',
      data: {
        labels: labels,
        datasets: datasetsNonEmpty.map(function (d) {
          return {
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
          };
        })
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
          x: {
            type: 'category',
            ticks: { autoSkip: true, maxTicksLimit: 12 },
            grid: { display: false }
          }
        },
        plugins: {
          legend: {
            display: false
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

    // Legenda personalizzata
    const legendContainer = document.getElementById('yieldLegend');
    let legendHTML = '<div class="chart-legend" role="list" aria-label="Legenda aree geografiche">';
    datasetsNonEmpty.forEach(function (d) {
      const colorClass = d.key.toLowerCase();
      legendHTML += '<span class="item ' + colorClass + '" role="listitem">' +
        '<span class="legend-dot"></span>' +
        '<span>' + d.label + '</span>' +
        '</span>';
    });
    legendHTML += '</div>';
    legendContainer.innerHTML = legendHTML;
  })();

  /* ===== Efficienza Idrica: Polar Area ===== */
  (function () {
    const el = document.getElementById('effPolar');
    if (!el) return;

    const labelsAreas = ['Nord', 'Centro', 'Sud'];
    const valuesAreas = [effNordKgM3, effCentroKgM3, effSudKgM3];
    const colors = colorsAll;

    let L = [], V = [], C = [];
    if (areaSel && String(areaSel).trim().length > 0) {
      const a = String(areaSel).toLowerCase();
      const idx = a === 'nord' ? 0 : (a === 'centro' ? 1 : (a === 'sud' ? 2 : -1));
      if (idx >= 0) {
        L = [labelsAreas[idx]];
        V = [valuesAreas[idx]];
        C = [colors[idx]];
      } else {
        L = labelsAreas;
        V = valuesAreas;
        C = colors;
      }
    } else {
      L = labelsAreas;
      V = valuesAreas;
      C = colors;
    }

    const vmax = Math.max.apply(null, V.filter(function (n) { return Number.isFinite(n); }).concat([100]));

    new Chart(el.getContext('2d'), {
      type: 'polarArea',
      data: {
        labels: L,
        datasets: [{
          data: V,
          backgroundColor: C.map(function (c) { return c + '40'; }),
          borderColor: C,
          borderWidth: 3
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            display: false
          },
          tooltip: {
            backgroundColor: 'rgba(255,255,255,0.95)',
            titleColor: '#333',
            bodyColor: '#666',
            borderColor: '#ddd',
            borderWidth: 1,
            padding: 12,
            callbacks: {
              label: function (ctx) {
                return ctx.label + ': ' + (+ctx.raw).toFixed(2) + ' Kg/m³';
              }
            }
          }
        },
        scales: {
          r: {
            suggestedMin: 0,
            suggestedMax: vmax * 1.15,
            ticks: {
              stepSize: Math.max(5, Math.round(vmax / 5)),
              callback: function (v) { return v + ' Kg/m³'; },
              font: { size: 11 }
            },
            grid: { color: 'rgba(0,0,0,0.08)' }
          }
        },
        animation: { animateRotate: true, animateScale: true }
      }
    });

    // Legenda personalizzata
    const legendContainer = document.getElementById('effLegend');
    let legendHTML = '<div class="chart-legend" role="list" aria-label="Legenda aree geografiche">';
    L.forEach(function (label) {
      const colorClass = label.toLowerCase();
      legendHTML += '<span class="item ' + colorClass + '" role="listitem">' +
        '<span class="legend-dot"></span>' +
        '<span>' + label + '</span>' +
        '</span>';
    });
    legendHTML += '</div>';
    legendContainer.innerHTML = legendHTML;
  })();

  /* ===== ECharts: Margine e Costi ===== */
  (function () {
    const areasAll = ['Nord', 'Centro', 'Sud'];
    const marginVals = [marginNord, marginCentro, marginSud];
    const costVals = [costNord, costCentro, costSud];

    const sel = (areaSel && String(areaSel).trim().length > 0) ? String(areaSel).toLowerCase() : null;

    function filtered(names, values) {
      if (!sel) return { names: names, values: values };
      const idx = sel === 'nord' ? 0 : (sel === 'centro' ? 1 : (sel === 'sud' ? 2 : -1));
      if (idx < 0) return { names: names, values: values };
      return { names: [names[idx]], values: [values[idx]] };
    }

    const M = filtered(areasAll, marginVals);
    const C_DATA = filtered(areasAll, costVals);

    function buildOption(titleText, cats, vals, mainColor) {
      return {
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' },
          backgroundColor: 'rgba(255,255,255,0.95)',
          borderColor: '#ddd',
          borderWidth: 1,
          textStyle: { color: '#333' },
          padding: 12,
          valueFormatter: function (v) { return '€ ' + Number(v).toFixed(2) + '/t'; }
        },
        grid: {
          left: 50,
          right: 30,
          top: 30,
          bottom: 40,
          containLabel: true
        },
        xAxis: {
          type: 'category',
          data: cats,
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
        series: [{
          type: 'bar',
          name: titleText,
          barWidth: '50%',
          itemStyle: {
            color: cats.length > 1
              ? function (p) { return colorByAreaName(p.name); }
              : mainColor,
            borderRadius: [6, 6, 0, 0],
            shadowColor: 'rgba(0,0,0,0.1)',
            shadowBlur: 10,
            shadowOffsetY: 4
          },
          emphasis: {
            itemStyle: {
              shadowBlur: 20,
              shadowOffsetY: 8
            }
          },
          data: vals
        }],
        animationDuration: 800,
        animationEasing: 'cubicOut'
      };
    }

    const echMargin = echarts.init(document.getElementById('echMargin'));
    const echCost = echarts.init(document.getElementById('echCost'));

    echMargin.setOption(buildOption('Margine medio (€/t)', M.names, M.values, '#0dcaf0'));
    echCost.setOption(buildOption('Costo medio (€/t)', C_DATA.names, C_DATA.values, '#6f42c1'));

    window.addEventListener('resize', function () {
      echMargin.resize();
      echCost.resize();
    });
  })();

  /* ===== Rischio Climatico: Gauge CSS ===== */
  (function () {
    const container = document.getElementById('riskGauges');
    if (!container) return;

    const areas = [
      { key: 'Nord', color: '#0d6efd', value: riskNord },
      { key: 'Centro', color: '#D96D00', value: riskCentro },
      { key: 'Sud', color: '#10b981', value: riskSud }
    ];

    function levelText(v) {
      if (v > 0.7) return '<span class="badge bg-danger">ALTO</span>';
      if (v >= 0.4) return '<span class="badge" style="background-color:var(--warning-accessible)">MEDIO</span>';
      return '<span class="badge bg-success">BASSO</span>';
    }

    let set = areas;
    if (areaSel && String(areaSel).trim().length > 0) {
      const a = String(areaSel).toLowerCase();
      set = areas.filter(function (x) { return x.key.toLowerCase() === a; });
      if (set.length === 0) set = areas;
    }

    container.innerHTML = set.map(function (a) {
      const colClass = set.length === 1 ? 'col-12' : (set.length === 2 ? 'col-md-6' : 'col-lg-4');
      return '<div class="' + colClass + '">' +
        '<div class="p-4 border rounded-3 h-100" style="background: linear-gradient(135deg, #fff 0%, ' + a.color + '08 100%)">' +
        '<div class="d-flex justify-content-between align-items-center mb-3 pb-2" style="border-bottom:3px solid ' + a.color + '">' +
        '<h3 class="h6 mb-0 fw-bold" style="color:' + a.color + '">' +
        '<i class="bi bi-geo-alt-fill me-2"></i>Area ' + a.key +
        '</h3>' +
        levelText(a.value) +
        '</div>' +
        '<p class="text-muted small mb-3">' +
        'Indice di rischio climatico: ' +
        '<span class="fw-bold fs-5" style="color:' + a.color + '">' + (Number(a.value) || 0).toFixed(2) + '</span>' +
        ' <span class="text-muted">(scala 0-1)</span>' +
        '</p>' +
        '<div class="risk-gauge" role="progressbar" ' +
        'aria-label="Indice di rischio climatico ' + a.key + '" ' +
        'aria-valuemin="0" aria-valuemax="1" ' +
        'aria-valuenow="' + (Number(a.value) || 0).toFixed(2) + '" ' +
        'style="--risk:' + (Number.isFinite(a.value) ? a.value : 0) + '">' +
        '<div class="risk-track"></div>' +
        '<div class="risk-thumb">' +
        '<span class="risk-value">' + (Number(a.value) || 0).toFixed(2) + '</span>' +
        '</div>' +
        '</div>' +
        '</div>' +
        '</div>';
    }).join('');
  })();
})();