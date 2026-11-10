import React, { useState } from 'react';
import { Property } from '../types/property';
import { formatUF, formatCLP, ufToClp } from '../utils/realEstateCalculations';
import { 
  Plus, 
  Minus, 
  Compass, 
  MapPin, 
  X, 
  ChevronRight, 
  ExternalLink,
  Layers,
  Bed,
  Bath,
  Maximize2
} from 'lucide-react';

interface InteractiveMapProps {
  properties: Property[];
  hoveredPropertyId: string | null;
  onHoverProperty: (id: string | null) => void;
  selectedProperty: Property | null;
  onSelectProperty: (property: Property | null) => void;
  onContactProperty: (property: Property) => void;
  priceDisplayMode: 'uf' | 'clp';
}

export const InteractiveMap: React.FC<InteractiveMapProps> = ({
  properties,
  hoveredPropertyId,
  onHoverProperty,
  selectedProperty,
  onSelectProperty,
  onContactProperty,
  priceDisplayMode
}) => {
  const [zoomLevel, setZoomLevel] = useState(1);
  const [searchOnMove, setSearchOnMove] = useState(true);
  const [mapLayer, setMapLayer] = useState<'calle' | 'relieve'>('calle');

  const handleZoomIn = () => setZoomLevel((z) => Math.min(z + 0.2, 1.8));
  const handleZoomOut = () => setZoomLevel((z) => Math.max(z - 0.2, 0.8));
  const handleReset = () => setZoomLevel(1);

  return (
    <div className="relative w-full h-full min-h-[500px] lg:min-h-[700px] bg-[#eef2f5] rounded-2xl overflow-hidden border border-slate-200 select-none shadow-inner">
      
      {/* Background Stylized Map Pattern (Chile / Santiago metro geography) */}
      <div 
        className="absolute inset-0 transition-transform duration-300 origin-center"
        style={{ transform: `scale(${zoomLevel})` }}
      >
        {/* Vector SVG map canvas representing Santiago, Chile topography */}
        <svg className="w-full h-full" viewBox="0 0 1000 1000" preserveAspectRatio="none">
          <defs>
            {/* Grid pattern */}
            <pattern id="street-grid" width="40" height="40" patternUnits="userSpaceOnUse">
              <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#e2e8f0" strokeWidth="1" />
            </pattern>
            {/* Andes Cordillera Gradient */}
            <linearGradient id="andes-gradient" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#e2e8f0" stopOpacity="0" />
              <stop offset="60%" stopColor="#cbd5e1" stopOpacity="0.4" />
              <stop offset="100%" stopColor="#94a3b8" stopOpacity="0.7" />
            </linearGradient>
          </defs>

          {/* Background land */}
          <rect width="1000" height="1000" fill={mapLayer === 'calle' ? '#f8fafc' : '#f1f5f9'} />
          <rect width="1000" height="1000" fill="url(#street-grid)" />

          {/* Cordillera de los Andes on the East */}
          <path
            d="M 800 0 L 1000 0 L 1000 1000 L 750 1000 Q 820 600 780 400 Q 810 200 800 0 Z"
            fill="url(#andes-gradient)"
          />

          {/* Río Mapocho traversal */}
          <path
            d="M 100 650 Q 300 620 500 520 T 900 380"
            fill="none"
            stroke="#93c5fd"
            strokeWidth="10"
            strokeLinecap="round"
            opacity="0.6"
          />

          {/* Autopista Américo Vespucio Ring Loop */}
          <ellipse
            cx="520"
            cy="500"
            rx="320"
            ry="280"
            fill="none"
            stroke="#fde047"
            strokeWidth="5"
            strokeDasharray="8 6"
            opacity="0.8"
          />

          {/* Autopista Central (Norte-Sur) */}
          <path
            d="M 500 50 L 500 950"
            fill="none"
            stroke="#fed7aa"
            strokeWidth="6"
            opacity="0.8"
          />

          {/* Cerro San Cristóbal Park green blob */}
          <ellipse cx="560" cy="420" rx="45" ry="35" fill="#bbf7d0" opacity="0.7" />
          {/* Cerro Manquehue */}
          <polygon points="720,280 770,350 670,350" fill="#cbd5e1" opacity="0.6" />

          {/* Comuna Labels on Map */}
          <text x="560" y="425" fill="#15803d" fontSize="13" fontWeight="bold" textAnchor="middle">
            Parque Metropolitano
          </text>
          <text x="890" y="250" fill="#64748b" fontSize="14" fontWeight="800" textAnchor="middle" letterSpacing="3">
            CORDILLERA DE LOS ANDES
          </text>

          <text x="660" y="660" fill="#475569" fontSize="15" fontWeight="bold">La Florida</text>
          <text x="320" y="600" fill="#475569" fontSize="15" fontWeight="bold">Maipú</text>
          <text x="630" y="850" fill="#475569" fontSize="15" fontWeight="bold">Puente Alto</text>
          <text x="730" y="520" fill="#475569" fontSize="15" fontWeight="bold">Peñalolén</text>
          <text x="760" y="320" fill="#475569" fontSize="15" fontWeight="bold">Las Condes</text>
          <text x="600" y="460" fill="#475569" fontSize="15" fontWeight="bold">Ñuñoa</text>
          <text x="460" y="140" fill="#475569" fontSize="15" fontWeight="bold">Chicureo / Colina</text>
        </svg>

        {/* Interactive Property Price Pins */}
        {properties.map((prop) => {
          const isHovered = hoveredPropertyId === prop.id;
          const isSelected = selectedProperty?.id === prop.id;
          const x = prop.coordinates.xPercent || 50;
          const y = prop.coordinates.yPercent || 50;

          return (
            <div
              key={prop.id}
              style={{ left: `${x}%`, top: `${y}%` }}
              className="absolute -translate-x-1/2 -translate-y-1/2 z-20 transition-all duration-200"
            >
              <button
                type="button"
                onClick={() => onSelectProperty(prop)}
                onMouseEnter={() => onHoverProperty(prop.id)}
                onMouseLeave={() => onHoverProperty(null)}
                className={`group relative px-2.5 py-1 rounded-full text-xs font-bold transition-all transform shadow-md flex items-center gap-1 ${
                  isHovered || isSelected
                    ? 'bg-[#0d5c56] text-white scale-115 ring-3 ring-white shadow-xl z-30'
                    : 'bg-white text-[#0f172a] hover:bg-[#0d5c56] hover:text-white border border-slate-300'
                }`}
                aria-label={`Ver casa en ${prop.comuna} por ${formatUF(prop.priceUf)}`}
              >
                <MapPin className={`w-3 h-3 ${isHovered || isSelected ? 'text-white' : 'text-[#0d5c56]'}`} />
                <span>{priceDisplayMode === 'uf' ? formatUF(prop.priceUf) : formatCLP(ufToClp(prop.priceUf))}</span>
              </button>

              {/* Pin pointing triangle */}
              <div 
                className={`w-0 h-0 border-x-4 border-x-transparent border-t-5 mx-auto transition-colors ${
                  isHovered || isSelected ? 'border-t-[#0d5c56]' : 'border-t-white'
                }`} 
              />
            </div>
          );
        })}
      </div>

      {/* Top Floating Controls */}
      <div className="absolute top-3 left-3 right-3 flex items-center justify-between pointer-events-none z-30">
        <div className="bg-white/95 backdrop-blur-md px-3 py-1.5 rounded-xl shadow-md border border-slate-200 text-xs font-semibold text-slate-700 pointer-events-auto flex items-center gap-2">
          <label className="flex items-center gap-2 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={searchOnMove}
              onChange={(e) => setSearchOnMove(e.target.checked)}
              className="w-3.5 h-3.5 rounded text-[#0d5c56] border-slate-300 focus:ring-[#0d5c56]"
            />
            <span>Buscar al mover el mapa</span>
          </label>
        </div>

        <div className="flex items-center gap-1.5 pointer-events-auto">
          <button
            type="button"
            onClick={() => setMapLayer(mapLayer === 'calle' ? 'relieve' : 'calle')}
            className="p-2 bg-white/95 hover:bg-white text-slate-700 rounded-xl shadow-md border border-slate-200 text-xs font-semibold flex items-center gap-1"
            title="Cambiar capa de mapa"
          >
            <Layers className="w-4 h-4 text-[#0d5c56]" />
            <span className="hidden sm:inline capitalize">{mapLayer}</span>
          </button>
        </div>
      </div>

      {/* Right Zoom & Recenter Controls */}
      <div className="absolute right-3 bottom-6 flex flex-col gap-1.5 z-30">
        <button
          type="button"
          onClick={handleZoomIn}
          className="w-9 h-9 bg-white hover:bg-slate-50 text-slate-700 rounded-xl shadow-md border border-slate-200 flex items-center justify-center font-bold focus:ring-2 focus:ring-[#0d5c56]"
          aria-label="Acercar mapa"
        >
          <Plus className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={handleZoomOut}
          className="w-9 h-9 bg-white hover:bg-slate-50 text-slate-700 rounded-xl shadow-md border border-slate-200 flex items-center justify-center font-bold focus:ring-2 focus:ring-[#0d5c56]"
          aria-label="Alejar mapa"
        >
          <Minus className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={handleReset}
          className="w-9 h-9 bg-white hover:bg-slate-50 text-slate-700 rounded-xl shadow-md border border-slate-200 flex items-center justify-center focus:ring-2 focus:ring-[#0d5c56]"
          title="Centrar Santiago"
          aria-label="Centrar mapa en Santiago"
        >
          <Compass className="w-4 h-4 text-[#0d5c56]" />
        </button>
      </div>

      {/* Bottom Popup Card for Selected Property */}
      {selectedProperty && (
        <div className="absolute left-3 right-3 bottom-3 sm:left-6 sm:w-80 bg-white rounded-2xl shadow-2xl border border-slate-200 p-3.5 z-40 animate-in fade-in slide-in-from-bottom-3">
          <div className="flex items-start justify-between gap-2 mb-2">
            <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-[#e8f5f4] text-[#0d5c56]">
              {selectedProperty.comuna}
            </span>
            <button
              onClick={() => onSelectProperty(null)}
              className="p-1 text-slate-400 hover:text-slate-600 rounded-full hover:bg-slate-100"
              aria-label="Cerrar vista previa"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          <div className="flex gap-3">
            {selectedProperty.images.length > 0 ? (
              <img
                src={selectedProperty.images[0]}
                alt={selectedProperty.title}
                className="w-20 h-20 rounded-xl object-cover shrink-0"
              />
            ) : (
              <div className="w-20 h-20 rounded-xl bg-slate-100 flex items-center justify-center shrink-0 text-slate-400">
                <MapPin className="w-6 h-6" />
              </div>
            )}

            <div className="flex-1 min-w-0">
              <h4 className="text-xs font-bold text-slate-900 line-clamp-1">
                {selectedProperty.title}
              </h4>
              <p className="text-[11px] text-slate-500 truncate mb-1">
                {selectedProperty.addressApprox}
              </p>
              
              <div className="text-sm font-extrabold text-[#0d5c56]">
                {priceDisplayMode === 'uf' 
                  ? formatUF(selectedProperty.priceUf) 
                  : formatCLP(ufToClp(selectedProperty.priceUf))}
              </div>

              <div className="flex items-center gap-2 text-[11px] text-slate-600 mt-1 font-medium">
                <span>{selectedProperty.dormitorios} dorms</span>
                <span>•</span>
                <span>{selectedProperty.banos} baños</span>
                <span>•</span>
                <span>{selectedProperty.m2Util} m²</span>
              </div>
            </div>
          </div>

          <div className="mt-2.5 pt-2 border-t border-slate-100 flex items-center gap-2">
            <button
              onClick={() => onContactProperty(selectedProperty)}
              className="flex-1 py-1.5 bg-[#e35238] hover:bg-[#c84128] text-white text-xs font-semibold rounded-lg shadow-sm"
            >
              Contactar
            </button>
          </div>
        </div>
      )}

    </div>
  );
};
