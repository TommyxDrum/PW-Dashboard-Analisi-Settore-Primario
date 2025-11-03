(function () {
 // Dati storici per grafici (ultimi 20 punti)
    const historyLimit = 20;
    const chartData = {
        labels: [],
        temp: { Nord: [], Centro: [], Sud: [] },
        humidity: { Nord: [], Centro: [], Sud: [] },
        wind: { Nord: [], Centro: [], Sud: [] },
        solar: { Nord: [], Centro: [], Sud: [] }
    };

    // Colori per area
    const areaColors = {
        Nord: '#0d6efd',
        Centro: '#D96D00',
        Sud: '#10b981'
    };

    // Inizializza grafici
    let tempChart, humidityChart, windChart, solarChart;

    function initCharts() {
        const commonOptions = {
            responsive: true,
            maintainAspectRatio: false,
            animation: {
                duration: 750,
                easing: 'easeInOutQuart'
            },
            scales: {
                x: {
                    display: true,
                    grid: {
                        display: false
                    },
                    ticks: {
                        font: {
                            size: 11,
                            weight: '500'
                        },
                        color: '#6c757d'
                    }
                },
                y: {
                    display: true,
                    grid: {
                        color: 'rgba(0,0,0,0.04)',
                        drawBorder: false
                    },
                    ticks: {
                        font: {
                            size: 11,
                            weight: '500'
                        },
                        color: '#6c757d',
                        padding: 8
                    }
                }
            },
            plugins: {
                legend: {
                    position: 'top',
                    labels: {
                        usePointStyle: true,
                        padding: 20,
                        font: {
                            size: 12,
                            weight: '600'
                        }
                    }
                },
                tooltip: {
                    backgroundColor: 'rgba(255, 255, 255, 0.95)',
                    titleColor: '#2d3748',
                    bodyColor: '#4a5568',
                    borderColor: 'rgba(0,0,0,0.1)',
                    borderWidth: 1,
                    padding: 12,
                    boxPadding: 6,
                    usePointStyle: true,
                    titleFont: {
                        size: 13,
                        weight: '700'
                    },
                    bodyFont: {
                        size: 12,
                        weight: '600'
                    }
                }
            },
            interaction: {
                intersect: false,
                mode: 'index'
            }
        };

        tempChart = new Chart(document.getElementById('tempChart'), {
            type: 'line',
            data: {
                labels: chartData.labels,
                datasets: Object.keys(areaColors).map(area => ({
                    label: area,
                    data: chartData.temp[area],
                    borderColor: areaColors[area],
                    backgroundColor: areaColors[area] + '15',
                    tension: 0.4,
                    fill: true,
                    borderWidth: 3,
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    pointBackgroundColor: '#fff',
                    pointBorderColor: areaColors[area],
                    pointBorderWidth: 2,
                    pointHoverBorderWidth: 3
                }))
            },
            options: {
                ...commonOptions,
                scales: {
                    ...commonOptions.scales,
                    y: {
                        ...commonOptions.scales.y,
                        title: {
                            display: true,
                            text: '°C',
                            font: {
                                size: 12,
                                weight: '700'
                            },
                            color: '#4a5568'
                        }
                    }
                }
            }
        });

        humidityChart = new Chart(document.getElementById('humidityChart'), {
            type: 'line',
            data: {
                labels: chartData.labels,
                datasets: Object.keys(areaColors).map(area => ({
                    label: area,
                    data: chartData.humidity[area],
                    borderColor: areaColors[area],
                    backgroundColor: areaColors[area] + '15',
                    tension: 0.4,
                    fill: true,
                    borderWidth: 3,
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    pointBackgroundColor: '#fff',
                    pointBorderColor: areaColors[area],
                    pointBorderWidth: 2,
                    pointHoverBorderWidth: 3
                }))
            },
            options: {
                ...commonOptions,
                scales: {
                    ...commonOptions.scales,
                    y: {
                        ...commonOptions.scales.y,
                        title: {
                            display: true,
                            text: '%',
                            font: {
                                size: 12,
                                weight: '700'
                            },
                            color: '#4a5568'
                        }
                    }
                }
            }
        });

        windChart = new Chart(document.getElementById('windChart'), {
            type: 'line',
            data: {
                labels: chartData.labels,
                datasets: Object.keys(areaColors).map(area => ({
                    label: area,
                    data: chartData.wind[area],
                    borderColor: areaColors[area],
                    backgroundColor: areaColors[area] + '15',
                    tension: 0.4,
                    fill: true,
                    borderWidth: 3,
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    pointBackgroundColor: '#fff',
                    pointBorderColor: areaColors[area],
                    pointBorderWidth: 2,
                    pointHoverBorderWidth: 3
                }))
            },
            options: {
                ...commonOptions,
                scales: {
                    ...commonOptions.scales,
                    y: {
                        ...commonOptions.scales.y,
                        title: {
                            display: true,
                            text: 'km/h',
                            font: {
                                size: 12,
                                weight: '700'
                            },
                            color: '#4a5568'
                        }
                    }
                }
            }
        });

        solarChart = new Chart(document.getElementById('solarChart'), {
            type: 'line',
            data: {
                labels: chartData.labels,
                datasets: Object.keys(areaColors).map(area => ({
                    label: area,
                    data: chartData.solar[area],
                    borderColor: areaColors[area],
                    backgroundColor: areaColors[area] + '15',
                    tension: 0.4,
                    fill: true,
                    borderWidth: 3,
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    pointBackgroundColor: '#fff',
                    pointBorderColor: areaColors[area],
                    pointBorderWidth: 2,
                    pointHoverBorderWidth: 3
                }))
            },
            options: {
                ...commonOptions,
                scales: {
                    ...commonOptions.scales,
                    y: {
                        ...commonOptions.scales.y,
                        title: {
                            display: true,
                            text: 'W/m²',
                            font: {
                                size: 12,
                                weight: '700'
                            },
                            color: '#4a5568'
                        }
                    }
                }
            }
        });
    }

    // Connessione SSE per dati meteo
    function connectWeatherStream() {
        const eventSource = new EventSource('/api/stream/weather');

        eventSource.addEventListener('weather-update', function(event) {
            const data = JSON.parse(event.data);
            console.log('🌤️ Dati meteo aggiornati:', data);

            updateWeatherCards(data);
            updateCharts(data);
            updateLastUpdateTime();
        });

        eventSource.onerror = function() {
            console.error('❌ Errore streaming meteo');
            eventSource.close();
            setTimeout(connectWeatherStream, 5000);
        };
    }

    function updateWeatherCards(data) {
        const container = document.getElementById('weatherCards');
        container.innerHTML = '';

        Object.entries(data.dataPerArea).forEach(([area, weather]) => {
            const areaClass = 'area-' + area.toLowerCase();
            const icon = getWeatherIcon(weather.condizioni);

            const card = `
                <div class="col-md-4">
                    <div class="weather-card ${areaClass}">
                        <div class="d-flex justify-content-between align-items-start mb-3">
                            <div>
                                <h4 class="area-title mb-0">${area}</h4>
                                <span class="conditions-badge">${weather.condizioni}</span>
                            </div>
                            <div class="weather-icon">${icon}</div>
                        </div>

                        <div class="weather-value">
                            ${weather.temperaturaC.toFixed(1)}°C
                        </div>

                        <div class="row g-3 mt-2">
                            <div class="col-6">
                                <div class="metric-box">
                                    <i class="bi bi-droplet-fill"></i>
                                    <strong>${weather.umiditaPct.toFixed(0)}%</strong>
                                    <div class="weather-label">Umidità</div>
                                </div>
                            </div>
                            <div class="col-6">
                                <div class="metric-box">
                                    <i class="bi bi-wind"></i>
                                    <strong>${weather.ventoKmh.toFixed(1)}</strong>
                                    <div class="weather-label">Vento km/h</div>
                                </div>
                            </div>
                            <div class="col-6">
                                <div class="metric-box">
                                    <i class="bi bi-cloud-rain-fill"></i>
                                    <strong>${weather.precipitazioniMm.toFixed(1)}</strong>
                                    <div class="weather-label">Pioggia mm</div>
                                </div>
                            </div>
                            <div class="col-6">
                                <div class="metric-box">
                                    <i class="bi bi-brightness-high-fill"></i>
                                    <strong>${weather.radiazioneSolare.toFixed(0)}</strong>
                                    <div class="weather-label">Radiazione W/m²</div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            container.innerHTML += card;
        });
    }

    function updateCharts(data) {
        // Aggiungi timestamp
        const now = new Date();
        const timeLabel = now.toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit', second: '2-digit' });

        chartData.labels.push(timeLabel);

        // Aggiungi dati per ogni area
        Object.entries(data.dataPerArea).forEach(([area, weather]) => {
            chartData.temp[area].push(weather.temperaturaC);
            chartData.humidity[area].push(weather.umiditaPct);
            chartData.wind[area].push(weather.ventoKmh);
            chartData.solar[area].push(weather.radiazioneSolare);
        });

        // Mantieni solo ultimi N punti
        if (chartData.labels.length > historyLimit) {
            chartData.labels.shift();
            Object.keys(areaColors).forEach(area => {
                chartData.temp[area].shift();
                chartData.humidity[area].shift();
                chartData.wind[area].shift();
                chartData.solar[area].shift();
            });
        }

        // Aggiorna grafici con animazione smooth
        tempChart.update('none');
        humidityChart.update('none');
        windChart.update('none');
        solarChart.update('none');
    }

    function getWeatherIcon(condizioni) {
        const iconMap = {
            'Soleggiato': '<i class="bi bi-sun-fill" style="color: #ffd700;"></i>',
            'Nuvoloso': '<i class="bi bi-cloud-fill" style="color: #e0e0e0;"></i>',
            'Pioggia': '<i class="bi bi-cloud-rain-heavy-fill" style="color: #87ceeb;"></i>',
            'Parzialmente nuvoloso': '<i class="bi bi-cloud-sun-fill" style="color: #ffd700;"></i>',
            'Temporale': '<i class="bi bi-cloud-lightning-rain-fill" style="color: #fff;"></i>',
            'Neve': '<i class="bi bi-cloud-snow-fill" style="color: #fff;"></i>',
            'Nebbia': '<i class="bi bi-cloud-fog2-fill" style="color: #d3d3d3;"></i>'
        };
        return iconMap[condizioni] || '<i class="bi bi-cloud-sun-fill" style="color: #ffd700;"></i>';
    }

    function updateLastUpdateTime() {
        const timeEl = document.getElementById('lastUpdateTime');
        if (timeEl) {
            const now = new Date();
            timeEl.textContent = now.toLocaleTimeString('it-IT');
        }
    }

    // Avvia tutto
    document.addEventListener('DOMContentLoaded', function() {
        console.log('Inizializzazione dashboard meteo...');
        initCharts();
        connectWeatherStream();
    });


})();