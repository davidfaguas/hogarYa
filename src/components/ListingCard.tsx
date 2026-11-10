import React, { useState } from 'react';
import { 
  Heart, 
  ChevronLeft, 
  ChevronRight, 
  Camera, 
  Video, 
  Sparkles, 
  Bed, 
  Bath, 
  Maximize2, 
  MapPin, 
  Calculator, 
  MessageCircle,
  Building,
  ImageOff,
  ArrowRight,
  Coins
} from 'lucide-react';
import { Property } from '../types/property';
import { 
  formatUF, 
  formatCLP, 
  ufToClp, 
  calculatePricePerM2Uf, 
  calculateMortgageDividend 
} from '../utils/realEstateCalculations';

interface ListingCardProps {
  property: Property;
  isFavorite: boolean;
  onToggleFavorite: (id: string) => void;
  isCompared: boolean;
  onToggleCompare: (id: string) => void;
  canCompare: boolean;
  priceDisplayMode: 'uf' | 'clp';
  onTogglePriceDisplayMode?: () => void;
  onCotizarClick: (property: Property) => void;
  onVerMasClick: (property: Property) => void;
  onWhatsAppClick: (property: Property) => void;
  onSimulateMortgage: (property: Property) => void;
  isHovered?: boolean;
  onHover?: (id: string | null) => void;
  compactListMode?: boolean;
}

export const ListingCard: React.FC<ListingCardProps> = ({
  property,
  isFavorite,
  onToggleFavorite,
  isCompared,
  onToggleCompare,
  canCompare,
  priceDisplayMode,
  onTogglePriceDisplayMode,
  onCotizarClick,
  onVerMasClick,
  onWhatsAppClick,
  onSimulateMortgage,
  isHovered = false,
  onHover,
  compactListMode = false
}) => {
  const [currentImageIndex, setCurrentImageIndex] = useState(0);

  const images = property.images && property.images.length > 0 ? property.images : [];
  const hasImages = images.length > 0;

  const nextImage = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!hasImages) return;
    setCurrentImageIndex((prev) => (prev + 1) % images.length);
  };

  const prevImage = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!hasImages) return;
    setCurrentImageIndex((prev) => (prev - 1 + images.length) % images.length);
  };

  const mortgage = calculateMortgageDividend(property.priceUf, 20, 25, 4.5);
  const pricePerM2 = calculatePricePerM2Uf(property.priceUf, property.m2Util);
  const priceClpVal = ufToClp(property.priceUf);

  return (
    <article
      onMouseEnter={() => onHover?.(property.id)}
      onMouseLeave={() => onHover?.(null)}
      onClick={() => onVerMasClick(property)}
      className={`group bg-white rounded-2xl border transition-all duration-200 overflow-hidden flex flex-col cursor-pointer ${
        isHovered
          ? 'border-[#0d9488] shadow-xl shadow-teal-950/10 -translate-y-0.5 ring-1 ring-[#0d9488]/20'
          : 'border-slate-200 hover:border-slate-300 shadow-xs hover:shadow-md'
      } ${compactListMode ? 'md:flex-row' : ''}`}
    >
      {/* 1. Photo Area */}
      <div className={`relative bg-slate-100 overflow-hidden select-none ${
        compactListMode ? 'md:w-72 md:shrink-0 aspect-[16/10] md:aspect-auto' : 'aspect-[16/11]'
      }`}>
        {hasImages ? (
          <>
            <img
              src={images[currentImageIndex]}
              alt={`${property.title} - Foto ${currentImageIndex + 1}`}
              loading="lazy"
              className="w-full h-full object-cover group-hover:scale-[1.02] transition-transform duration-300"
            />

            {/* Inmobiliaria / Corredora Badge */}
            <div className="absolute bottom-2.5 left-2.5 bg-white/95 backdrop-blur-xs px-2.5 py-1 rounded-md shadow flex items-center gap-1.5 z-10 border border-slate-100">
              <span className="w-2 h-2 rounded-full bg-[#0d9488]" />
              <span className="text-[10px] font-extrabold uppercase tracking-tight text-slate-800 truncate max-w-[130px]">
                {property.seller.name}
              </span>
            </div>

            {/* Arrows */}
            {images.length > 1 && (
              <>
                <button
                  type="button"
                  onClick={prevImage}
                  className="absolute left-2 top-1/2 -translate-y-1/2 w-7 h-7 rounded-full bg-white/90 hover:bg-white text-slate-800 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity shadow-md focus:opacity-100 z-10"
                  aria-label="Foto anterior"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  onClick={nextImage}
                  className="absolute right-2 top-1/2 -translate-y-1/2 w-7 h-7 rounded-full bg-white/90 hover:bg-white text-slate-800 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity shadow-md focus:opacity-100 z-10"
                  aria-label="Siguiente foto"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>

                {/* Photo Counter */}
                <div className="absolute bottom-2.5 right-2.5 px-2 py-0.5 rounded-full bg-black/60 backdrop-blur-xs text-white text-[10px] font-semibold tracking-wider z-10">
                  {currentImageIndex + 1}/{images.length}
                </div>
              </>
            )}
          </>
        ) : (
          <div className="w-full h-full flex flex-col items-center justify-center bg-slate-100 text-slate-400 p-6 text-center">
            <ImageOff className="w-8 h-8 text-slate-400 mb-1" />
            <p className="text-xs font-semibold text-slate-600">Fotografía profesional en preparación</p>
          </div>
        )}

        {/* Top Left: Badges */}
        <div className="absolute top-2.5 left-2.5 flex flex-wrap gap-1 z-10">
          {property.status === 'nueva' && (
            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-[#1e293b] text-white shadow-xs">
              Nuevo
            </span>
          )}
          {property.badges.includes('tour_virtual') && (
            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-900/80 backdrop-blur-xs text-white shadow-xs flex items-center gap-1">
              <Camera className="w-3 h-3" /> 360°
            </span>
          )}
          {property.badges.includes('video') && (
            <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-900/80 backdrop-blur-xs text-white shadow-xs flex items-center gap-1">
              <Video className="w-3 h-3" /> Video
            </span>
          )}
        </div>

        {/* Top Right: Favorite Button */}
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onToggleFavorite(property.id);
          }}
          className={`absolute top-2.5 right-2.5 w-8 h-8 rounded-full flex items-center justify-center transition-all z-10 ${
            isFavorite
              ? 'bg-white text-rose-600 shadow-md ring-2 ring-rose-500/20'
              : 'bg-white/90 hover:bg-white text-slate-600 hover:text-rose-500 shadow-sm'
          }`}
          aria-label={isFavorite ? 'Eliminar de favoritos' : 'Guardar en favoritos'}
        >
          <Heart className={`w-4 h-4 ${isFavorite ? 'fill-rose-500 text-rose-500' : ''}`} />
        </button>
      </div>

      {/* 2. Card Body */}
      <div className="p-4 flex-1 flex flex-col justify-between">
        <div>
          {/* Comuna */}
          <div className="text-xs font-bold text-slate-900 uppercase tracking-tight flex items-center justify-between">
            <span>{property.comuna}</span>
            <span className="text-[10px] text-slate-400 font-medium lowercase">
              {property.status === 'nueva' ? 'nuevo' : 'usado'}
            </span>
          </div>

          {/* Subtype info */}
          <div className="text-[11px] text-slate-500 font-medium mt-0.5">
            Casas en venta en {property.neighborhood || property.comuna}
          </div>

          {/* Title */}
          <h3 className="text-sm font-bold text-slate-900 line-clamp-1 hover:text-[#0d9488] transition-colors mt-1">
            {property.title}
          </h3>

          {/* Key metrics with pipes */}
          <div className="text-xs text-slate-600 font-medium mt-2 flex items-center gap-1.5 flex-wrap">
            <span><strong>{property.dormitorios}</strong> dorm</span>
            <span className="text-slate-300">|</span>
            <span><strong>{property.banos}</strong> baños</span>
            <span className="text-slate-300">|</span>
            <span><strong>{property.m2Util}</strong> m² útil</span>
            {property.estacionamientos > 0 && (
              <>
                <span className="text-slate-300">|</span>
                <span><strong>{property.estacionamientos}</strong> estac</span>
              </>
            )}
          </div>

          {/* Price Section with Dual-Hierarchy and Card-Level Switch */}
          <div className="mt-3 pt-2.5 border-t border-slate-100">
            <div className="flex items-center justify-between">
              <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400">
                Valor venta
              </span>

              {/* Inline card currency switch requested by user */}
              {onTogglePriceDisplayMode && (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onTogglePriceDisplayMode();
                  }}
                  className="flex items-center gap-1 px-1.5 py-0.5 rounded-md bg-slate-100 hover:bg-slate-200 border border-slate-200 text-[10px] font-bold text-slate-700 transition-colors"
                  title="Cambiar vista a UF o CLP"
                >
                  <Coins className="w-2.5 h-2.5 text-[#0d9488]" />
                  <span className={`px-1 rounded ${priceDisplayMode === 'uf' ? 'bg-[#0d9488] text-white' : 'text-slate-500'}`}>UF</span>
                  <span className={`px-1 rounded ${priceDisplayMode === 'clp' ? 'bg-[#0d9488] text-white' : 'text-slate-500'}`}>CLP</span>
                </button>
              )}
            </div>

            {/* Dynamic Price Display */}
            {priceDisplayMode === 'uf' ? (
              <div className="mt-1 flex items-baseline gap-1.5">
                <span className="text-lg sm:text-xl font-black text-slate-950 tracking-tight leading-none">
                  {formatUF(property.priceUf)}
                </span>
                <span className="text-xs text-slate-400">/</span>
                <span className="text-xs font-semibold text-slate-500">
                  {formatCLP(priceClpVal)}
                </span>
              </div>
            ) : (
              <div className="mt-1 flex items-baseline gap-1.5">
                <span className="text-lg sm:text-xl font-black text-[#0d9488] tracking-tight leading-none">
                  {formatCLP(priceClpVal)}
                </span>
                <span className="text-xs text-slate-400">/</span>
                <span className="text-xs font-bold text-slate-700">
                  {formatUF(property.priceUf)}
                </span>
              </div>
            )}
          </div>

          {/* Monthly Dividend Teaser */}
          <div className="mt-2 text-[11px] text-[#0f766e] font-semibold bg-teal-50/80 px-2.5 py-1 rounded-md flex items-center justify-between border border-teal-100/60">
            <span>Dividendo desde: <strong>{formatCLP(mortgage.dividendClp)}/mes</strong></span>
            <span className="text-[10px] text-slate-500">{pricePerM2} UF/m²</span>
          </div>
        </div>

        {/* 3. Action CTAs */}
        <div className="mt-3 pt-3 border-t border-slate-100 space-y-2">
          
          {/* Main Action: "Cotizar" */}
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onCotizarClick(property);
            }}
            className="w-full py-2.5 bg-[#1e293b] hover:bg-[#0f172a] active:scale-[0.99] text-white font-bold text-xs rounded-xl shadow-xs transition-all flex items-center justify-center gap-1.5 cursor-pointer"
          >
            <span>Cotizar con Inmobiliaria</span>
          </button>

          {/* Secondary Actions Row */}
          <div className="flex items-center justify-between gap-2 text-xs">
            {/* Compare Checkbox */}
            <label 
              onClick={(e) => e.stopPropagation()}
              className="flex items-center gap-1.5 cursor-pointer text-slate-600 hover:text-[#0d9488] select-none text-[11px] font-semibold"
            >
              <input
                type="checkbox"
                checked={isCompared}
                disabled={!isCompared && !canCompare}
                onChange={() => onToggleCompare(property.id)}
                className="w-3.5 h-3.5 rounded text-[#0d9488] border-slate-300 focus:ring-[#0d9488] cursor-pointer"
              />
              <span>Comparar</span>
            </label>

            {/* WhatsApp Quick Link */}
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onWhatsAppClick(property);
              }}
              className="flex items-center gap-1 text-[11px] font-bold text-emerald-700 hover:text-emerald-800 transition-colors"
            >
              <MessageCircle className="w-3.5 h-3.5 text-emerald-600" />
              <span>WhatsApp</span>
            </button>

            {/* "Ver más" link */}
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onVerMasClick(property);
              }}
              className="text-[11px] font-bold text-[#0d9488] hover:underline flex items-center gap-0.5"
            >
              <span>Ver más</span>
              <ArrowRight className="w-3 h-3" />
            </button>
          </div>

        </div>

      </div>
    </article>
  );
};
