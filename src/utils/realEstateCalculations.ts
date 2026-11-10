import { Property, PropertyFilters, SortOption } from '../types/property';

// Current official UF in Chile (approx Nov 2026 value)
export const CURRENT_UF_VALUE_CLP = 37850;

/**
 * Format UF value with Chilean thousands separator (period) and no decimals or 1 decimal if needed
 */
export function formatUF(value: number): string {
  const rounded = Math.round(value);
  const formatted = rounded.toLocaleString('es-CL');
  return `UF ${formatted}`;
}

/**
 * Format Chilean Peso (CLP) with $ symbol and period thousands separator
 */
export function formatCLP(value: number): string {
  const rounded = Math.round(value);
  const formatted = rounded.toLocaleString('es-CL');
  return `$${formatted}`;
}

/**
 * Convert UF to CLP using given or default UF rate
 */
export function ufToClp(uf: number, rate: number = CURRENT_UF_VALUE_CLP): number {
  return Math.round(uf * rate);
}

/**
 * Convert CLP to UF using given or default UF rate
 */
export function clpToUf(clp: number, rate: number = CURRENT_UF_VALUE_CLP): number {
  return Number((clp / rate).toFixed(2));
}

/**
 * Calculate UF price per m² of usable area
 */
export function calculatePricePerM2Uf(priceUf: number, m2Util: number): number {
  if (m2Util <= 0) return 0;
  return Number((priceUf / m2Util).toFixed(1));
}

export interface MortgageEstimate {
  dividendClp: number;
  dividendUf: number;
  pieUf: number;
  pieClp: number;
  loanUf: number;
  minIncomeSuggestedClp: number;
}

/**
 * Calculates monthly mortgage payment in Chile using French amortization system.
 * In Chile, mortgages are signed in UF with an annual fixed rate (e.g. 4.6% + UF).
 * The borrower pays monthly dividend converted to CLP at current UF.
 * Standard rule: Household monthly income must be at least 4x the dividend.
 *
 * @param priceUf Property price in UF
 * @param piePercent Down payment percentage (default 20%)
 * @param years Loan duration in years (default 25 years)
 * @param annualRatePercent Annual interest rate (default 4.5%)
 * @param ufRate Current CLP value of 1 UF
 */
export function calculateMortgageDividend(
  priceUf: number,
  piePercent: number = 20,
  years: number = 25,
  annualRatePercent: number = 4.5,
  ufRate: number = CURRENT_UF_VALUE_CLP
): MortgageEstimate {
  const pieRatio = piePercent / 100;
  const pieUf = Math.round(priceUf * pieRatio);
  const pieClp = ufToClp(pieUf, ufRate);

  const loanUf = priceUf - pieUf;
  if (loanUf <= 0) {
    return {
      dividendClp: 0,
      dividendUf: 0,
      pieUf,
      pieClp,
      loanUf: 0,
      minIncomeSuggestedClp: 0,
    };
  }

  const monthlyRate = (annualRatePercent / 100) / 12;
  const totalMonths = years * 12;

  // French amortization formula: M = P * [r(1+r)^n] / [(1+r)^n - 1]
  const factor = Math.pow(1 + monthlyRate, totalMonths);
  const dividendUfRaw = loanUf * ((monthlyRate * factor) / (factor - 1));
  const dividendUf = Number(dividendUfRaw.toFixed(2));
  const dividendClp = ufToClp(dividendUfRaw, ufRate);

  // Chilean banks require dividend to be max 25% of monthly salary (income >= 4x dividend)
  const minIncomeSuggestedClp = Math.round(dividendClp * 4);

  return {
    dividendClp,
    dividendUf,
    pieUf,
    pieClp,
    loanUf,
    minIncomeSuggestedClp,
  };
}

/**
 * Filter properties based on active filter set and optional search query
 */
export function filterProperties(
  properties: Property[],
  filters: PropertyFilters,
  searchQuery: string = ''
): Property[] {
  return properties.filter((item) => {
    // Operation match
    if (filters.operation && item.operation !== filters.operation) {
      return false;
    }

    // Property Type match
    if (filters.propertyType && filters.propertyType !== 'todos' && item.propertyType !== filters.propertyType) {
      return false;
    }

    // Status match (nueva / usada)
    if (filters.estado && filters.estado !== 'todas' && item.status !== filters.estado) {
      return false;
    }

    // Comuna / Search query
    const searchTrimmed = searchQuery.trim().toLowerCase();
    if (searchTrimmed) {
      const matchText = `${item.comuna} ${item.region} ${item.addressApprox} ${item.neighborhood || ''} ${item.title}`.toLowerCase();
      if (!matchText.includes(searchTrimmed)) {
        return false;
      }
    }

    // Region match if selected
    if (filters.region && filters.region !== 'Todas' && filters.region !== 'todas') {
      if (!item.region.toLowerCase().includes(filters.region.toLowerCase())) {
        return false;
      }
    }

    // Comuna filter if selected
    if (filters.comuna && filters.comuna !== 'Todas' && filters.comuna !== 'todas') {
      if (!item.comuna.toLowerCase().includes(filters.comuna.toLowerCase())) {
        return false;
      }
    }

    // Dormitorios
    if (filters.dormitorios !== null) {
      if (filters.dormitorios >= 4) {
        if (item.dormitorios < 4) return false;
      } else {
        if (item.dormitorios !== filters.dormitorios) return false;
      }
    }

    // Baños
    if (filters.banos !== null) {
      if (filters.banos >= 4) {
        if (item.banos < 4) return false;
      } else {
        if (item.banos < filters.banos) return false;
      }
    }

    // Price UF Min
    if (filters.priceMinUf !== null && filters.priceMinUf > 0) {
      if (item.priceUf < filters.priceMinUf) return false;
    }

    // Price UF Max
    if (filters.priceMaxUf !== null && filters.priceMaxUf > 0) {
      if (item.priceUf > filters.priceMaxUf) return false;
    }

    // Usable Area m² Min
    if (filters.m2Min !== null && filters.m2Min > 0) {
      if (item.m2Util < filters.m2Min) return false;
    }

    // Usable Area m² Max
    if (filters.m2Max !== null && filters.m2Max > 0) {
      if (item.m2Util > filters.m2Max) return false;
    }

    // Tour virtual
    if (filters.tourVirtualOnly) {
      if (!item.hasVirtualTour && !item.badges.includes('tour_virtual')) return false;
    }

    // Video
    if (filters.videoOnly) {
      if (!item.hasVideo && !item.badges.includes('video')) return false;
    }

    // Subsidio
    if (filters.subsidioOnly) {
      if (!item.badges.includes('ds19') && !item.badges.includes('ds1')) return false;
    }

    // Parking
    if (filters.parkingOnly) {
      if (item.estacionamientos <= 0) return false;
    }

    // Verified
    if (filters.verifiedOnly) {
      if (!item.badges.includes('verificada')) return false;
    }

    return true;
  });
}

/**
 * Sort properties based on selected criteria
 */
export function sortProperties(properties: Property[], sortOption: SortOption): Property[] {
  const list = [...properties];
  switch (sortOption) {
    case 'menor_precio':
      return list.sort((a, b) => a.priceUf - b.priceUf);
    case 'mayor_precio':
      return list.sort((a, b) => b.priceUf - a.priceUf);
    case 'menor_precio_m2':
      return list.sort((a, b) => {
        const m2A = calculatePricePerM2Uf(a.priceUf, a.m2Util);
        const m2B = calculatePricePerM2Uf(b.priceUf, b.m2Util);
        return m2A - m2B;
      });
    case 'mas_recientes':
      return list.sort((a, b) => new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime());
    case 'relevancia':
    default:
      return list.sort((a, b) => {
        // Featured first, then verified, then default order
        const scoreA = (a.featured ? 10 : 0) + (a.badges.includes('verificada') ? 5 : 0);
        const scoreB = (b.featured ? 10 : 0) + (b.badges.includes('verificada') ? 5 : 0);
        return scoreB - scoreA;
      });
  }
}
