export type PropertyType = 
  | 'casa' 
  | 'departamento' 
  | 'oficina' 
  | 'local' 
  | 'terreno' 
  | 'parcela' 
  | 'bodega' 
  | 'estacionamiento' 
  | 'industrial' 
  | 'vacacional';

export type OperationType = 'comprar' | 'arrendar';

export type PropertyStatus = 'todas' | 'nueva' | 'usada';

export type BadgeType = 'nueva' | 'tour_virtual' | 'video' | 'verificada' | 'bajo_precio' | 'ds19' | 'ds1';

export interface SellerInfo {
  name: string;
  type: 'corredora' | 'inmobiliaria' | 'propietario';
  logo?: string;
  rating?: number;
  phone: string;
  whatsapp: string;
  email: string;
}

export interface Coordinates {
  lat: number;
  lng: number;
  xPercent?: number; // for interactive vector canvas map
  yPercent?: number;
}

export interface Property {
  id: string;
  title: string;
  propertyType: PropertyType;
  operation: OperationType;
  status: 'nueva' | 'usada';
  priceUf: number;
  priceClp?: number; // computed or explicit
  originalPriceUf?: number; // if price dropped
  comuna: string;
  region: string;
  addressApprox: string;
  neighborhood?: string;
  dormitorios: number; // 0 = studio
  banos: number;
  m2Util: number;
  m2Total: number;
  estacionamientos: number;
  bodegas?: number;
  gastosComunesClp?: number;
  yearBuilt?: number;
  badges: BadgeType[];
  images: string[];
  seller: SellerInfo;
  coordinates: Coordinates;
  description: string;
  featured?: boolean;
  publishedAt: string;
  hasVirtualTour?: boolean;
  hasVideo?: boolean;
}

export interface PropertyFilters {
  operation: OperationType;
  propertyType: PropertyType | 'todos';
  comuna: string;
  region: string;
  estado: PropertyStatus;
  dormitorios: number | null; // null = any, 0 = studio, 4 = 4+
  banos: number | null; // null = any, 4 = 4+
  priceMinUf: number | null;
  priceMaxUf: number | null;
  m2Min: number | null;
  m2Max: number | null;
  tourVirtualOnly: boolean;
  videoOnly: boolean;
  subsidioOnly: boolean;
  parkingOnly: boolean;
  verifiedOnly: boolean;
}

export type SortOption = 
  | 'relevancia' 
  | 'menor_precio' 
  | 'mayor_precio' 
  | 'mas_recientes' 
  | 'menor_precio_m2';

export type ViewMode = 'split' | 'grid' | 'map' | 'list';

export type AppStateMode = 'normal' | 'loading' | 'empty' | 'error' | 'no_photo';

export type ViewportMode = 'desktop' | 'mobile' | 'responsive';
