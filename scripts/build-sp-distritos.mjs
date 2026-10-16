// Gera data/sp-distritos.geojson com os 96 distritos de São Paulo e a população do Censo 2022.
// Limites: GeoSampa (Prefeitura de São Paulo). População: IBGE/SIDRA, tabela 9923.
// Uso: node scripts/build-sp-distritos.mjs

import { mkdir, writeFile } from 'node:fs/promises';

const GEOSAMPA = 'https://wfs.geosampa.prefeitura.sp.gov.br/geoserver/geoportal/wfs'
  + '?service=WFS&version=2.0.0&request=GetFeature&typeNames=geoportal:distrito_municipal'
  + '&outputFormat=application/json&srsName=EPSG:4326';
const SIDRA = 'https://servicodados.ibge.gov.br/api/v3/agregados/9923/periodos/2022/variaveis/93'
  + '?localidades=N10[N6[3550308]]&classificacao=1[6795]';
const OUTPUT = 'data/sp-distritos.geojson';

const normalize = (name) => name
  .replace(/ - São Paulo - SP$/, '')
  .normalize('NFD').replace(/\p{Diacritic}/gu, '')
  .toUpperCase().replace(/\s+/g, ' ').trim();

const round = (coordinates) => Array.isArray(coordinates[0])
  ? coordinates.map(round)
  : coordinates.map((value) => Math.round(value * 1e6) / 1e6);

async function fetchJson(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${response.status} ao baixar ${url}`);
  }
  return response.json();
}

const [districts, census] = await Promise.all([fetchJson(GEOSAMPA), fetchJson(SIDRA)]);

const population = new Map();
for (const series of census[0].resultados[0].series) {
  population.set(normalize(series.localidade.nome), {
    name: series.localidade.nome.replace(/ - São Paulo - SP$/, ''),
    code: series.localidade.id,
    population: Number(series.serie['2022']),
  });
}

const features = districts.features.map((feature) => {
  const key = normalize(feature.properties.nm_distrito_municipal);
  const census2022 = population.get(key);
  if (!census2022) {
    throw new Error(`Distrito sem população no Censo: ${feature.properties.nm_distrito_municipal}`);
  }
  population.delete(key);
  return {
    type: 'Feature',
    properties: {
      name: census2022.name,
      population: census2022.population,
      ibge_code: census2022.code,
      region: feature.properties.nm_regiao_05,
    },
    geometry: { type: feature.geometry.type, coordinates: round(feature.geometry.coordinates) },
  };
});

if (population.size > 0) {
  throw new Error(`Distritos do Censo sem limite no GeoSampa: ${[...population.keys()].join(', ')}`);
}

features.sort((a, b) => a.properties.name.localeCompare(b.properties.name, 'pt-BR'));
await mkdir('data', { recursive: true });
await writeFile(OUTPUT, JSON.stringify({ type: 'FeatureCollection', features }));

const total = features.reduce((sum, f) => sum + f.properties.population, 0);
console.log(`${features.length} distritos gravados em ${OUTPUT} (população total: ${total})`);
