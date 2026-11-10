import React, { useState } from 'react';
import { 
  X, 
  Bed, 
  Bath, 
  Maximize2, 
  Trees, 
  Car, 
  MapPin, 
  Building, 
  MessageCircle, 
  Calculator, 
  Share2, 
  Heart, 
  ChevronLeft, 
  ChevronRight, 
  Sparkles
} from 'lucide-react';
import { Property } from '../types/property';
import { formatUF, formatCLP, ufToClp, calculatePricePerM2Uf, calculateMortgageDividend } from '../utils/realEstateCalculations';

interface PropertyDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  property: Property | null;
  isFavorite: boolean;
  onToggleFavorite: (id: string) => void;
  onOpenCotizar: (property: Property) => void;
  onOpenWhatsApp: (property: Property) => void;
  onOpenMortgageSimulator: (property: Property) => void;
}

export const PropertyDetailModal: React.FC<PropertyDetailModalProps> = ({
  isOpen,
  onClose,
  property,
  isFavorite,
  onToggleFavorite,
  onOpenCotizar,
  onOpenWhatsApp,
  onOpenMortgageSimulator,
}) => {
  const [activePhotoIdx, setActivePhotoIdx] = useState(0);

  if (!isOpen || !property) return null;

  const priceClp = ufToClp(property.priceUf);
  const pricePerM2 = calculatePricePerM2Uf(property.priceUf, property.m2Util);
  const mortgage = calculateMortgageDividend(property.priceUf, 20, 25, 4.5);
  const images = property.images && property.images.length > 0 ? property.images : ['https://images.unsplash.com/photo-1600585154340-be6161a56a0c?w=1200&q=80'];

  return (
    <div 
      className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-5 bg-black/70 backdrop-blur-xs animate-in fade-in duration-200"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
    >
      <div 
        className="relative w-full max-w-4xl bg-white rounded-2xl shadow-2xl overflow-hidden border border-slate-200 flex flex-col max-h-[92vh] animate-in zoom-in-95 duration-200"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Top bar */}
        <div className="flex items-center justify-between px-6 py-3.5 border-b border-slate-200 bg-white">
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold text-[#0d9488] bg-teal-50 px-2.5 py-1 rounded-full uppercase tracking-wider border border-teal-200/60">
              {property.status === 'nueva' ? 'Proyecto Nuevo' : 'Propiedad Usada'}
            </span>
            <span className="text-xs text-slate-500 font-medium">
              Publicado en HogarYa Chile
            </span>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => onToggleFavorite(property.id)}
              className={`p-2 rounded-full border transition-colors ${
                isFavorite 
                  ? 'bg-rose-50 border-rose-200 text-rose-500' 
                  : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'
              }`}
              title="Guardar en favoritos"
            >
              <Heart className={`w-4 h-4 ${isFavorite ? 'fill-rose-500' : ''}`} />
            </button>
            <button
              type="button"
              onClick={() => {
                if (navigator.share) {
                  navigator.share({ title: property.title, url: window.location.href });
                } else {
                  alert('Enlace copiado al portapapeles');
                }
              }}
              className="p-2 rounded-full bg-white border border-slate-200 text-slate-600 hover:bg-slate-50 transition-colors"
              title="Compartir propiedad"
            >
              <Share2 className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={onClose}
              className="p-2 rounded-full bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors cursor-pointer"
              aria-label="Cerrar modal"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Scrollable details */}
        <div className="overflow-y-auto flex-1 p-6 space-y-6">
          
          {/* Main Photo Gallery */}
          <div className="space-y-3">
            <div className="relative aspect-[16/9] sm:aspect-[21/9] rounded-xl overflow-hidden bg-slate-100">
              <img 
                src={images[activePhotoIdx]} 
                alt={`${property.title} - Foto ${activePhotoIdx + 1}`}
                className="w-full h-full object-cover"
              />
              {images.length > 1 && (
                <>
                  <button
                    type="button"
                    onClick={() => setActivePhotoIdx((prev) => (prev - 1 + images.length) % images.length)}
                    className="absolute left-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-white/90 hover:bg-white text-slate-900 flex items-center justify-center shadow-lg transition-all"
                  >
                    <ChevronLeft className="w-5 h-5" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setActivePhotoIdx((prev) => (prev + 1) % images.length)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-white/90 hover:bg-white text-slate-900 flex items-center justify-center shadow-lg transition-all"
                  >
                    <ChevronRight className="w-5 h-5" />
                  </button>
                  <div className="absolute bottom-3 right-3 px-3 py-1 bg-black/70 backdrop-blur-xs text-white text-xs font-semibold rounded-full">
                    {activePhotoIdx + 1} de {images.length} fotos
                  </div>
                </>
              )}
            </div>

            {/* Thumbnail strip */}
            {images.length > 1 && (
              <div className="flex gap-2 overflow-x-auto pb-1">
                {images.map((img, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => setActivePhotoIdx(idx)}
                    className={`relative w-20 h-14 rounded-lg overflow-hidden shrink-0 border-2 transition-all ${
                      idx === activePhotoIdx ? 'border-[#0d9488] ring-2 ring-[#0d9488]/20' : 'border-transparent opacity-70 hover:opacity-100'
                    }`}
                  >
                    <img src={img} alt="Miniatura" className="w-full h-full object-cover" />
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Title & Price Header */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
            <div>
              <div className="flex items-center gap-1.5 text-xs text-slate-500 font-semibold mb-1">
                <MapPin className="w-4 h-4 text-[#0d9488]" />
                <span>{property.addressApprox}, {property.comuna}</span>
              </div>
              <h2 className="text-xl sm:text-2xl font-bold text-slate-900">
                {property.title}
              </h2>
            </div>

            <div className="text-left sm:text-right">
              <span className="text-xs text-slate-400 font-bold uppercase tracking-wider">Valor propiedad</span>
              <div className="text-2xl sm:text-3xl font-black text-slate-900 leading-tight">
                {formatUF(property.priceUf)}
              </div>
              <div className="text-xs text-slate-500">
                ~ {formatCLP(priceClp)} CLP · <span className="font-semibold text-[#0d9488]">{pricePerM2} UF/m²</span>
              </div>
            </div>
          </div>

          {/* Key Specs Matrix */}
          <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 p-4 bg-slate-50 rounded-xl border border-slate-200 text-center">
            <div className="p-2">
              <div className="flex items-center justify-center text-[#0d9488] mb-1">
                <Bed className="w-5 h-5" />
              </div>
              <div className="text-lg font-bold text-slate-900">{property.dormitorios}</div>
              <div className="text-xs text-slate-500">Dormitorios</div>
            </div>
            <div className="p-2">
              <div className="flex items-center justify-center text-[#0d9488] mb-1">
                <Bath className="w-5 h-5" />
              </div>
              <div className="text-lg font-bold text-slate-900">{property.banos}</div>
              <div className="text-xs text-slate-500">Baños</div>
            </div>
            <div className="p-2">
              <div className="flex items-center justify-center text-[#0d9488] mb-1">
                <Maximize2 className="w-5 h-5" />
              </div>
              <div className="text-lg font-bold text-slate-900">{property.m2Util} m²</div>
              <div className="text-xs text-slate-500">Superficie Útil</div>
            </div>
            <div className="p-2">
              <div className="flex items-center justify-center text-[#0d9488] mb-1">
                <Trees className="w-5 h-5" />
              </div>
              <div className="text-lg font-bold text-slate-900">{property.m2Total} m²</div>
              <div className="text-xs text-slate-500">Terreno Total</div>
            </div>
            <div className="p-2 col-span-2 sm:col-span-1">
              <div className="flex items-center justify-center text-[#0d9488] mb-1">
                <Car className="w-5 h-5" />
              </div>
              <div className="text-lg font-bold text-slate-900">{property.estacionamientos}</div>
              <div className="text-xs text-slate-500">Estacionamientos</div>
            </div>
          </div>

          {/* Description */}
          <div className="space-y-2">
            <h3 className="text-xs font-bold text-slate-900 uppercase tracking-wider">
              Descripción de la propiedad
            </h3>
            <p className="text-xs sm:text-sm text-slate-700 leading-relaxed">
              {property.description}
            </p>
          </div>

          {/* Mortgage Teaser Box */}
          <div className="p-4 rounded-xl bg-gradient-to-r from-teal-50 to-slate-50 border border-teal-200/60 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-2 text-xs font-bold text-[#0d9488]">
                <Calculator className="w-4 h-4" />
                <span>Simulador de Crédito Hipotecario HogarYa</span>
              </div>
              <p className="text-xs text-slate-700">
                Paga un dividendo estimado desde <strong>{formatCLP(mortgage.dividendClp)}/mes</strong> (80% financiamiento a 25 años).
              </p>
            </div>
            <button
              type="button"
              onClick={() => onOpenMortgageSimulator(property)}
              className="px-4 py-2 bg-[#0d9488] hover:bg-[#0f766e] text-white font-semibold text-xs rounded-lg transition-colors shrink-0 shadow-sm cursor-pointer"
            >
              Simular crédito
            </button>
          </div>

          {/* Seller / Inmobiliaria info */}
          <div className="p-4 bg-slate-50 rounded-xl border border-slate-200 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-[#0f172a] text-white flex items-center justify-center font-bold text-sm">
                <Building className="w-5 h-5" />
              </div>
              <div>
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Publicado por
                </span>
                <h4 className="text-sm font-bold text-slate-900">{property.seller.name}</h4>
                <p className="text-xs text-slate-500">{property.seller.phone} · {property.seller.email}</p>
              </div>
            </div>
          </div>

        </div>

        {/* Bottom Action Footer */}
        <div className="px-6 py-4 bg-slate-50 border-t border-slate-200 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="hidden sm:block">
            <span className="text-xs text-slate-500">Valor referencial:</span>
            <div className="text-base font-black text-slate-900">
              {formatUF(property.priceUf)} <span className="text-xs font-normal text-slate-500">({formatCLP(priceClp)})</span>
            </div>
          </div>

          <div className="flex items-center gap-2 w-full sm:w-auto">
            <button
              type="button"
              onClick={() => onOpenWhatsApp(property)}
              className="flex-1 sm:flex-initial flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl border border-emerald-600 text-emerald-700 bg-white hover:bg-emerald-50 text-xs font-bold transition-colors cursor-pointer"
            >
              <MessageCircle className="w-4 h-4 text-emerald-600" />
              <span>WhatsApp</span>
            </button>
            <button
              type="button"
              onClick={() => onOpenCotizar(property)}
              className="flex-1 sm:flex-initial flex items-center justify-center gap-2 px-6 py-2.5 rounded-xl bg-[#0d9488] hover:bg-[#0f766e] text-white text-xs font-bold transition-all shadow-md cursor-pointer"
            >
              <Building className="w-4 h-4" />
              <span>Cotizar con Inmobiliaria</span>
            </button>
          </div>
        </div>

      </div>
    </div>
  );
};
