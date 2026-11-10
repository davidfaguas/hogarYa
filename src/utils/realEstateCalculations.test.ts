import test from 'node:test';
import assert from 'node:assert/strict';
import {
  formatUF,
  formatCLP,
  ufToClp,
  clpToUf,
  calculatePricePerM2Uf,
  calculateMortgageDividend,
  filterProperties,
  sortProperties,
  CURRENT_UF_VALUE_CLP
} from './realEstateCalculations';
import { Property, PropertyFilters } from '../types/property';

test('formatUF formats correctly with es-CL formatting', () => {
  assert.equal(formatUF(5890), 'UF 5.890');
  assert.equal(formatUF(12450.4), 'UF 12.450');
});

test('formatCLP formats correctly with $ symbol and periods', () => {
  assert.equal(formatCLP(150000000), '$150.000.000');
  assert.equal(formatCLP(620000), '$620.000');
});

test('ufToClp and clpToUf converts bidirectionally accurately', () => {
  const uf = 5000;
  const expectedClp = 5000 * CURRENT_UF_VALUE_CLP;
  assert.equal(ufToClp(uf), expectedClp);
  assert.equal(clpToUf(expectedClp), uf);
});

test('calculatePricePerM2Uf calculates UF per square meter properly', () => {
  // 6000 UF / 120 m2 = 50.0 UF/m2
  assert.equal(calculatePricePerM2Uf(6000, 120), 50.0);
  assert.equal(calculatePricePerM2Uf(5490, 138), 39.8);
  assert.equal(calculatePricePerM2Uf(5000, 0), 0);
});

test('calculateMortgageDividend computes French amortization in UF & CLP and min income', () => {
  // House of 5000 UF, 20% down payment (1000 UF), 4000 UF loan, 25 years, 4.5% annual rate
  const res = calculateMortgageDividend(5000, 20, 25, 4.5);
  assert.equal(res.pieUf, 1000);
  assert.equal(res.loanUf, 4000);
  // Monthly dividend should be approximately 22.2 UF
  assert.ok(res.dividendUf > 20 && res.dividendUf < 25, `Expected dividendUf around 22, got ${res.dividendUf}`);
  assert.ok(res.dividendClp > 750000 && res.dividendClp < 950000, `Expected dividendClp around $840.000, got ${res.dividendClp}`);
  // Suggested minimum income should be 4x dividend
  assert.equal(res.minIncomeSuggestedClp, res.dividendClp * 4);
});

test('filterProperties handles price range and bedroom filters', () => {
  const dummyProps: Property[] = [
    {
      id: '1',
      title: 'Casa La Florida',
      propertyType: 'casa',
      operation: 'comprar',
      status: 'usada',
      priceUf: 4500,
      comuna: 'La Florida',
      region: 'Metropolitana de Santiago',
      addressApprox: 'Av. Rojas Magallanes',
      dormitorios: 3,
      banos: 2,
      m2Util: 110,
      m2Total: 200,
      estacionamientos: 2,
      badges: ['verificada'],
      images: ['https://example.com/1.jpg'],
      seller: {
        name: 'Inmobiliaria A',
        type: 'corredora',
        phone: '+56911111111',
        whatsapp: '56911111111',
        email: 'a@example.com'
      },
      coordinates: { lat: -33.52, lng: -70.58 },
      description: 'Hermosa casa',
      publishedAt: '2026-03-01'
    },
    {
      id: '2',
      title: 'Casa Las Condes',
      propertyType: 'casa',
      operation: 'comprar',
      status: 'nueva',
      priceUf: 12000,
      comuna: 'Las Condes',
      region: 'Metropolitana de Santiago',
      addressApprox: 'Camino El Alba',
      dormitorios: 4,
      banos: 4,
      m2Util: 250,
      m2Total: 500,
      estacionamientos: 3,
      badges: ['nueva', 'tour_virtual'],
      images: ['https://example.com/2.jpg'],
      seller: {
        name: 'Inmobiliaria B',
        type: 'inmobiliaria',
        phone: '+56922222222',
        whatsapp: '56922222222',
        email: 'b@example.com'
      },
      coordinates: { lat: -33.40, lng: -70.54 },
      description: 'Lujo en Las Condes',
      publishedAt: '2026-03-10'
    }
  ];

  const baseFilters: PropertyFilters = {
    operation: 'comprar',
    propertyType: 'casa',
    comuna: '',
    region: '',
    estado: 'todas',
    dormitorios: null,
    banos: null,
    priceMinUf: null,
    priceMaxUf: 6000,
    m2Min: null,
    m2Max: null,
    tourVirtualOnly: false,
    videoOnly: false,
    subsidioOnly: false,
    parkingOnly: false,
    verifiedOnly: false
  };

  const filtered = filterProperties(dummyProps, baseFilters);
  assert.equal(filtered.length, 1);
  assert.equal(filtered[0].id, '1');

  // Test sort
  const sortedDesc = sortProperties(dummyProps, 'mayor_precio');
  assert.equal(sortedDesc[0].id, '2');
  assert.equal(sortedDesc[1].id, '1');
});
