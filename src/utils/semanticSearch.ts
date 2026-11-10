import { Property, PropertyFilters, SortOption } from '../types/property';
import { CHILE_COMMUNES } from '../data/mockProperties';

/**
 * Semantic Concept Clusters for Chilean Real Estate
 */
export const SEMANTIC_CONCEPTS = {
  piscina: {
    label: 'Piscina',
    keywords: ['piscina', 'pileta', 'alberca', 'reja peineta', 'sin fin', 'temperada', 'hidromasaje', 'jacuzzi', 'piscina de'],
  },
  metro: {
    label: 'Cerca del Metro',
    keywords: [
      'metro',
      'estacion de metro',
      'estación de metro',
      'cercana a metro',
      'cercano a metro',
      'cercana al metro',
      'cercano al metro',
      'cerca del metro',
      'cerca de metro',
      'pasos del metro',
      'caminando de estacion',
      'locomocion',
      'transporte',
      'conectividad'
    ],
  },
  quincho: {
    label: 'Quincho / Asador',
    keywords: ['quincho', 'asador', 'parrilla', 'bbq', 'barbecue', 'quincho techado', 'quincho integrado', 'asados'],
  },
  patio_jardin: {
    label: 'Jardín / Patio',
    keywords: ['patio', 'jardin', 'terraza', 'antejardin', 'patio trasero', 'patio ingles', 'areas verdes', 'pasto', 'riego', 'arboles'],
  },
  condominio_seguridad: {
    label: 'Condominio / Seguridad',
    keywords: ['condominio', 'seguridad', 'guardia', '24/7', 'cerrado', 'microbarrio', 'acceso controlado', 'porton', 'conserje', 'conserjeria'],
  },
  estacionamiento: {
    label: 'Estacionamiento',
    keywords: ['estacionamiento', 'estacionamientos', 'garage', 'cochera', 'vehiculo', 'vehiculos', 'autos', 'auto'],
  },
  subsidio: {
    label: 'Subsidio Habitacional',
    keywords: ['subsidio', 'ds19', 'ds1', 'tramo', 'bono pie', 'fogaes'],
  },
  vista: {
    label: 'Vista Panorámica',
    keywords: ['vista', 'mar', 'cordillera', 'panoramica', 'despejada', 'mirador', 'bahia', 'acantilado'],
  },
  moderna_nueva: {
    label: 'Nueva / Estrenar',
    keywords: ['nueva', 'estrenar', 'entrega inmediata', 'proyecto nuevo', 'en verde', 'en blanco', 'townhouse', 'termopanel', 'cuarzo'],
  }
};

export const SEMANTIC_SEARCH_SUGGESTIONS = [
  'casa con piscina',
  'cerca del metro',
  'con quincho y patio',
  'subsidio DS19 / DS1',
  'condominio seguridad 24/7',
  'vista a la cordillera'
];

/**
 * Normalizes text: removes accents, converts to lowercase, trims punctuation
 */
export function normalizeText(text: string): string {
  if (!text) return '';
  return text
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[.,\/#!$%\^&\*;:{}=\-_`~()?"']/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Spanish stop words to remove for token-based matching
 */
const STOP_WORDS = new Set([
  'de', 'con', 'el', 'la', 'los', 'las', 'un', 'una', 'unos', 'unas',
  'y', 'o', 'en', 'del', 'al', 'para', 'por', 'que', 'a', 'su', 'sus'
]);

/**
 * Result of semantic relevance scoring
 */
export interface SemanticScoreResult {
  property: Property;
  score: number;
  matchedConcepts: string[];
  reasons: string[];
}

/**
 * Calculates semantic score of a property given a free-text search query.
 */
export function calculateSemanticScore(property: Property, rawQuery: string): SemanticScoreResult {
  const query = normalizeText(rawQuery);
  if (!query) {
    return {
      property,
      score: 1,
      matchedConcepts: [],
      reasons: []
    };
  }

  let score = 0;
  const matchedConcepts: string[] = [];
  const reasons: string[] = [];

  const normTitle = normalizeText(property.title);
  const normDesc = normalizeText(property.description);
  const normComuna = normalizeText(property.comuna);
  const normAddress = normalizeText(property.addressApprox);
  const normNeighborhood = normalizeText(property.neighborhood || '');
  const combinedText = `${normTitle} ${normDesc} ${normComuna} ${normAddress} ${normNeighborhood}`;

  // 1. Direct phrase matching
  if (combinedText.includes(query)) {
    score += 60;
    reasons.push('Coincidencia textual exacta');
  } else if (normTitle.includes(query)) {
    score += 80;
    reasons.push('Coincidencia en el título');
  }

  // 2. Commune matching
  for (const c of CHILE_COMMUNES) {
    const normC = normalizeText(c);
    if (query.includes(normC)) {
      if (normComuna.includes(normC)) {
        score += 45;
        reasons.push(`Comuna ${c}`);
      }
    }
  }

  // 3. Semantic Concept Analysis
  for (const [key, concept] of Object.entries(SEMANTIC_CONCEPTS)) {
    // Check if query is looking for this concept
    const queryHasConcept = concept.keywords.some((kw) => {
      const normKw = normalizeText(kw);
      return query.includes(normKw);
    });

    if (queryHasConcept) {
      // Check if property satisfies this concept
      let propertyMatchesConcept = false;

      // Textual match in property details
      for (const kw of concept.keywords) {
        const normKw = normalizeText(kw);
        if (combinedText.includes(normKw)) {
          propertyMatchesConcept = true;
          break;
        }
      }

      // Metadata / attribute match
      if (key === 'piscina' && (normDesc.includes('piscina') || normTitle.includes('piscina') || normDesc.includes('pileta'))) {
        propertyMatchesConcept = true;
      }
      if (key === 'metro' && (normDesc.includes('metro') || normTitle.includes('metro') || normAddress.includes('metro'))) {
        propertyMatchesConcept = true;
      }
      if (key === 'estacionamiento' && property.estacionamientos > 0) {
        propertyMatchesConcept = true;
      }
      if (key === 'subsidio' && (property.badges.includes('ds19') || property.badges.includes('ds1') || normDesc.includes('subsidio'))) {
        propertyMatchesConcept = true;
      }
      if (key === 'moderna_nueva' && property.status === 'nueva') {
        propertyMatchesConcept = true;
      }

      if (propertyMatchesConcept) {
        score += 50;
        matchedConcepts.push(concept.label);
        reasons.push(`Característica: ${concept.label}`);
      }
    }
  }

  // 4. Token-level matching (removing stop words)
  const tokens = query
    .split(' ')
    .filter((t) => t.length > 2 && !STOP_WORDS.has(t));

  let matchedTokensCount = 0;
  for (const token of tokens) {
    if (normTitle.includes(token)) {
      score += 20;
      matchedTokensCount++;
    } else if (normDesc.includes(token)) {
      score += 15;
      matchedTokensCount++;
    } else if (normComuna.includes(token) || normNeighborhood.includes(token)) {
      score += 18;
      matchedTokensCount++;
    } else if (normAddress.includes(token)) {
      score += 10;
      matchedTokensCount++;
    }
  }

  // Bonus for overall quality and completeness if there is a match
  if (score > 0) {
    if (property.featured) score += 5;
    if (property.badges.includes('verificada')) score += 3;
    if (property.hasVirtualTour) score += 2;
  }

  return {
    property,
    score,
    matchedConcepts,
    reasons
  };
}

/**
 * Filter and rank properties based on structured filters and free-text semantic query
 */
export function filterAndRankProperties(
  properties: Property[],
  filters: PropertyFilters,
  searchQuery: string,
  sortOption: SortOption
): Property[] {
  const query = searchQuery.trim();

  // 1. Structured attribute filtering
  const filtered = properties.filter((item) => {
    // Operation match (comprar / arrendar)
    if (filters.operation && item.operation !== filters.operation) {
      return false;
    }

    // Property type match (casa, depto, etc.)
    if (filters.propertyType && filters.propertyType !== 'todos' && item.propertyType !== filters.propertyType) {
      return false;
    }

    // Status match (nueva / usada)
    if (filters.estado && filters.estado !== 'todas' && item.status !== filters.estado) {
      return false;
    }

    // Region filter
    if (filters.region && filters.region !== 'Todas las regiones' && filters.region !== 'todas') {
      if (!normalizeText(item.region).includes(normalizeText(filters.region))) {
        return false;
      }
    }

    // Explicit Comuna dropdown filter
    if (filters.comuna && filters.comuna !== 'Todas' && filters.comuna !== 'todas') {
      if (!normalizeText(item.comuna).includes(normalizeText(filters.comuna))) {
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

    // Price UF
    if (filters.priceMinUf !== null && filters.priceMinUf > 0) {
      if (item.priceUf < filters.priceMinUf) return false;
    }
    if (filters.priceMaxUf !== null && filters.priceMaxUf > 0) {
      if (item.priceUf > filters.priceMaxUf) return false;
    }

    // Usable Area m²
    if (filters.m2Min !== null && filters.m2Min > 0) {
      if (item.m2Util < filters.m2Min) return false;
    }
    if (filters.m2Max !== null && filters.m2Max > 0) {
      if (item.m2Util > filters.m2Max) return false;
    }

    // Tour virtual
    if (filters.tourVirtualOnly && !item.hasVirtualTour && !item.badges.includes('tour_virtual')) {
      return false;
    }

    // Video
    if (filters.videoOnly && !item.hasVideo && !item.badges.includes('video')) {
      return false;
    }

    // Subsidio
    if (filters.subsidioOnly && !item.badges.includes('ds19') && !item.badges.includes('ds1')) {
      return false;
    }

    // Parking
    if (filters.parkingOnly && item.estacionamientos <= 0) {
      return false;
    }

    // Verified
    if (filters.verifiedOnly && !item.badges.includes('verificada')) {
      return false;
    }

    return true;
  });

  // 2. If no free-text query, use standard sorting
  if (!query) {
    return sortPropertiesList(filtered, sortOption);
  }

  // 3. Compute semantic score for all candidate properties
  const scored = filtered.map((item) => ({
    item,
    res: calculateSemanticScore(item, query)
  }));

  // Filter out items that have zero relevance score when a specific query was provided
  const semanticMatches = scored.filter((s) => s.res.score > 0);

  // If query had specific terms but no exact match, fallback gracefully to top scores
  const candidates = semanticMatches.length > 0 ? semanticMatches : scored;

  // 4. Sort properties:
  // If sortOption is 'relevancia', rank strictly by semanticScore descending!
  if (sortOption === 'relevancia') {
    candidates.sort((a, b) => b.res.score - a.res.score);
    return candidates.map((c) => c.item);
  }

  // If user explicitly chose another sort criteria (menor precio, etc.), sort by that
  return sortPropertiesList(candidates.map((c) => c.item), sortOption);
}

/**
 * Standard sort helper for property arrays
 */
function sortPropertiesList(list: Property[], sortOption: SortOption): Property[] {
  const result = [...list];
  switch (sortOption) {
    case 'menor_precio':
      return result.sort((a, b) => a.priceUf - b.priceUf);
    case 'mayor_precio':
      return result.sort((a, b) => b.priceUf - a.priceUf);
    case 'menor_precio_m2':
      return result.sort((a, b) => {
        const m2A = a.priceUf / Math.max(a.m2Util, 1);
        const m2B = b.priceUf / Math.max(b.m2Util, 1);
        return m2A - m2B;
      });
    case 'mas_recientes':
      return result.sort((a, b) => new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime());
    case 'relevancia':
    default:
      return result.sort((a, b) => {
        const scoreA = (a.featured ? 10 : 0) + (a.badges.includes('verificada') ? 5 : 0);
        const scoreB = (b.featured ? 10 : 0) + (b.badges.includes('verificada') ? 5 : 0);
        return scoreB - scoreA;
      });
  }
}
