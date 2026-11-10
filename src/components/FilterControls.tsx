import React from 'react';
import { 
  PropertyFilters, 
  PropertyStatus 
} from '../types/property';
import { CHILE_REGIONS, CHILE_COMMUNES } from '../data/mockProperties';
import { 
  RotateCcw, 
  BellRing, 
  Check, 
  Sparkles, 
  ShieldCheck, 
  Video, 
  Camera, 
  Car,
  BadgeDollarSign
} from 'lucide-react';

interface FilterControlsProps {
  filters: PropertyFilters;
  onFilterChange: (updated: Partial<PropertyFilters>) => void;
  onResetFilters: () => void;
  onOpenSaveAlertModal: () => void;
  isMobile?: boolean;
}

export const FilterControls: React.FC<FilterControlsProps> = ({
  filters,
  onFilterChange,
  onResetFilters,
  onOpenSaveAlertModal,
  isMobile = false
}) => {
  // Preset UF Bands
  const ufPriceBands = [
    { label: 'Hasta 4.000 UF', min: null, max: 4000 },
    { label: '4.001 – 6.000 UF', min: 4001, max: 6000 },
    { label: '6.001 – 10.000 UF', min: 6001, max: 10000 },
    { label: 'Más de 10.000 UF', min: 10001, max: null }
  ];

  // Preset m² Bands
  const m2Bands = [
    { label: 'Hasta 50 m²', min: null, max: 50 },
    { label: '50 – 100 m²', min: 50, max: 100 },
    { label: 'Más de 100 m²', min: 101, max: null }
  ];

  const bedroomsOptions = [
    { label: 'Todos', value: null },
    { label: 'Studio', value: 0 },
    { label: '1', value: 1 },
    { label: '2', value: 2 },
    { label: '3', value: 3 },
    { label: '4+', value: 4 }
  ];

  const bathroomsOptions = [
    { label: 'Todos', value: null },
    { label: '1+', value: 1 },
    { label: '2+', value: 2 },
    { label: '3+', value: 3 },
    { label: '4+', value: 4 }
  ];

  return (
    <div className="space-y-6 text-slate-800">
      
      {/* 1. Header with Reset & Save Alert */}
      <div className="flex items-center justify-between pb-3 border-b border-slate-200">
        <span className="text-sm font-bold text-slate-900 tracking-tight">Filtros de búsqueda</span>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onResetFilters}
            className="flex items-center gap-1 text-xs font-semibold text-slate-500 hover:text-[#e35238] transition-colors p-1"
            title="Restablecer todos los filtros"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Limpiar</span>
          </button>
        </div>
      </div>

      {/* 2. Estado: Nueva / Usada */}
      <div>
        <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 mb-2">
          Estado de la propiedad
        </label>
        <div className="grid grid-cols-3 gap-1.5 p-1 bg-slate-100 rounded-xl">
          {(['todas', 'nueva', 'usada'] as PropertyStatus[]).map((status) => {
            const isSelected = filters.estado === status;
            const labels = { todas: 'Todas', nueva: 'Nueva', usada: 'Usada' };
            return (
              <button
                key={status}
                type="button"
                onClick={() => onFilterChange({ estado: status })}
                className={`py-1.5 px-2 rounded-lg text-xs font-semibold capitalize transition-all ${
                  isSelected
                    ? 'bg-[#0d5c56] text-white shadow-xs'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-200/50'
                }`}
              >
                {labels[status]}
              </button>
            );
          })}
        </div>
      </div>

      {/* 3. Región & Comuna */}
      <div className="space-y-3">
        <div>
          <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 mb-1.5">
            Región
          </label>
          <select
            value={filters.region}
            onChange={(e) => onFilterChange({ region: e.target.value })}
            className="w-full text-xs font-medium bg-white border border-slate-300 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:ring-2 focus:ring-[#0d5c56] focus:border-[#0d5c56]"
          >
            {CHILE_REGIONS.map((r) => (
              <option key={r} value={r === 'Todas las regiones' ? '' : r}>
                {r}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 mb-1.5">
            Comuna
          </label>
          <select
            value={filters.comuna}
            onChange={(e) => onFilterChange({ comuna: e.target.value })}
            className="w-full text-xs font-medium bg-white border border-slate-300 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:ring-2 focus:ring-[#0d5c56] focus:border-[#0d5c56]"
          >
            <option value="">Todas las comunas</option>
            {CHILE_COMMUNES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* 4. Precio en UF (Bands + Custom) */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <label className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Rango de Precio (UF)
          </label>
          <span className="text-[11px] text-[#0d5c56] font-semibold">1 UF ≈ $37.850</span>
        </div>

        {/* Quick Bands */}
        <div className="grid grid-cols-2 gap-1.5 mb-3">
          {ufPriceBands.map((band) => {
            const isSelected =
              filters.priceMinUf === band.min && filters.priceMaxUf === band.max;
            return (
              <button
                key={band.label}
                type="button"
                onClick={() => {
                  if (isSelected) {
                    onFilterChange({ priceMinUf: null, priceMaxUf: null });
                  } else {
                    onFilterChange({ priceMinUf: band.min, priceMaxUf: band.max });
                  }
                }}
                className={`py-1.5 px-2 rounded-lg text-xs font-medium text-left border transition-all truncate ${
                  isSelected
                    ? 'border-[#0d5c56] bg-[#0d5c56] text-white font-semibold'
                    : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50 hover:border-slate-300'
                }`}
              >
                {band.label}
              </button>
            );
          })}
        </div>

        {/* Custom Min / Max UF */}
        <div className="flex items-center gap-2">
          <div className="flex-1">
            <input
              type="number"
              placeholder="Min UF"
              value={filters.priceMinUf || ''}
              onChange={(e) =>
                onFilterChange({
                  priceMinUf: e.target.value ? Number(e.target.value) : null
                })
              }
              className="w-full text-xs bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 focus:outline-none focus:ring-1 focus:ring-[#0d5c56] focus:border-[#0d5c56]"
            />
          </div>
          <span className="text-slate-400 text-xs">-</span>
          <div className="flex-1">
            <input
              type="number"
              placeholder="Max UF"
              value={filters.priceMaxUf || ''}
              onChange={(e) =>
                onFilterChange({
                  priceMaxUf: e.target.value ? Number(e.target.value) : null
                })
              }
              className="w-full text-xs bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 focus:outline-none focus:ring-1 focus:ring-[#0d5c56] focus:border-[#0d5c56]"
            />
          </div>
        </div>
      </div>

      {/* 5. Dormitorios */}
      <div>
        <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 mb-2">
          Dormitorios
        </label>
        <div className="flex flex-wrap gap-1.5">
          {bedroomsOptions.map((opt) => {
            const isSelected = filters.dormitorios === opt.value;
            return (
              <button
                key={opt.label}
                type="button"
                onClick={() => onFilterChange({ dormitorios: opt.value })}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold border transition-all ${
                  isSelected
                    ? 'border-[#0d5c56] bg-[#0d5c56] text-white shadow-2xs'
                    : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:bg-slate-50'
                }`}
              >
                {opt.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* 6. Baños */}
      <div>
        <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 mb-2">
          Baños
        </label>
        <div className="flex flex-wrap gap-1.5">
          {bathroomsOptions.map((opt) => {
            const isSelected = filters.banos === opt.value;
            return (
              <button
                key={opt.label}
                type="button"
                onClick={() => onFilterChange({ banos: opt.value })}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold border transition-all ${
                  isSelected
                    ? 'border-[#0d5c56] bg-[#0d5c56] text-white shadow-2xs'
                    : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:bg-slate-50'
                }`}
              >
                {opt.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* 7. Superficie Útil m² */}
      <div>
        <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 mb-2">
          Superficie útil (m²)
        </label>
        <div className="grid grid-cols-3 gap-1.5 mb-2.5">
          {m2Bands.map((band) => {
            const isSelected =
              filters.m2Min === band.min && filters.m2Max === band.max;
            return (
              <button
                key={band.label}
                type="button"
                onClick={() => {
                  if (isSelected) {
                    onFilterChange({ m2Min: null, m2Max: null });
                  } else {
                    onFilterChange({ m2Min: band.min, m2Max: band.max });
                  }
                }}
                className={`py-1.5 px-1.5 rounded-lg text-[11px] font-medium border text-center transition-all ${
                  isSelected
                    ? 'border-[#0d5c56] bg-[#0d5c56] text-white font-semibold'
                    : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
                }`}
              >
                {band.label}
              </button>
            );
          })}
        </div>

        <div className="flex items-center gap-2">
          <input
            type="number"
            placeholder="Min m²"
            value={filters.m2Min || ''}
            onChange={(e) =>
              onFilterChange({
                m2Min: e.target.value ? Number(e.target.value) : null
              })
            }
            className="flex-1 text-xs bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 focus:outline-none focus:ring-1 focus:ring-[#0d5c56]"
          />
          <span className="text-slate-400 text-xs">-</span>
          <input
            type="number"
            placeholder="Max m²"
            value={filters.m2Max || ''}
            onChange={(e) =>
              onFilterChange({
                m2Max: e.target.value ? Number(e.target.value) : null
              })
            }
            className="flex-1 text-xs bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 focus:outline-none focus:ring-1 focus:ring-[#0d5c56]"
          />
        </div>
      </div>

      {/* 8. Multimedia & Beneficios (Checkboxes) */}
      <div className="pt-2 border-t border-slate-200">
        <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 mb-2.5">
          Multimedia y características
        </label>
        
        <div className="space-y-2">
          {/* Tour virtual */}
          <label className="flex items-center justify-between text-xs text-slate-700 cursor-pointer select-none hover:text-[#0d5c56]">
            <div className="flex items-center gap-2">
              <Camera className="w-4 h-4 text-slate-500" />
              <span>Con Tour virtual 360°</span>
            </div>
            <input
              type="checkbox"
              checked={filters.tourVirtualOnly}
              onChange={(e) => onFilterChange({ tourVirtualOnly: e.target.checked })}
              className="w-4 h-4 text-[#0d5c56] rounded border-slate-300 focus:ring-[#0d5c56]"
            />
          </label>

          {/* Video */}
          <label className="flex items-center justify-between text-xs text-slate-700 cursor-pointer select-none hover:text-[#0d5c56]">
            <div className="flex items-center gap-2">
              <Video className="w-4 h-4 text-slate-500" />
              <span>Con Video HD</span>
            </div>
            <input
              type="checkbox"
              checked={filters.videoOnly}
              onChange={(e) => onFilterChange({ videoOnly: e.target.checked })}
              className="w-4 h-4 text-[#0d5c56] rounded border-slate-300 focus:ring-[#0d5c56]"
            />
          </label>

          {/* Verificadas */}
          <label className="flex items-center justify-between text-xs text-slate-700 cursor-pointer select-none hover:text-[#0d5c56]">
            <div className="flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-emerald-600" />
              <span>Propiedad Verificada</span>
            </div>
            <input
              type="checkbox"
              checked={filters.verifiedOnly}
              onChange={(e) => onFilterChange({ verifiedOnly: e.target.checked })}
              className="w-4 h-4 text-[#0d5c56] rounded border-slate-300 focus:ring-[#0d5c56]"
            />
          </label>

          {/* Estacionamiento */}
          <label className="flex items-center justify-between text-xs text-slate-700 cursor-pointer select-none hover:text-[#0d5c56]">
            <div className="flex items-center gap-2">
              <Car className="w-4 h-4 text-slate-500" />
              <span>Con Estacionamiento</span>
            </div>
            <input
              type="checkbox"
              checked={filters.parkingOnly}
              onChange={(e) => onFilterChange({ parkingOnly: e.target.checked })}
              className="w-4 h-4 text-[#0d5c56] rounded border-slate-300 focus:ring-[#0d5c56]"
            />
          </label>

          {/* Subsidio */}
          <label className="flex items-center justify-between text-xs text-slate-700 cursor-pointer select-none hover:text-[#0d5c56]">
            <div className="flex items-center gap-2">
              <BadgeDollarSign className="w-4 h-4 text-amber-600" />
              <span>Acepta Subsidio Minvu (DS19 / DS1)</span>
            </div>
            <input
              type="checkbox"
              checked={filters.subsidioOnly}
              onChange={(e) => onFilterChange({ subsidioOnly: e.target.checked })}
              className="w-4 h-4 text-[#0d5c56] rounded border-slate-300 focus:ring-[#0d5c56]"
            />
          </label>
        </div>
      </div>

      {/* 9. Action: "Guardar búsqueda" Button */}
      <div className="pt-3 border-t border-slate-200">
        <button
          type="button"
          onClick={onOpenSaveAlertModal}
          className="w-full flex items-center justify-center gap-2 py-2.5 px-3 bg-[#e8f5f4] hover:bg-[#d4eee9] text-[#0d5c56] border border-[#0d5c56]/30 rounded-xl text-xs font-bold transition-all"
        >
          <BellRing className="w-4 h-4" />
          <span>Guardar búsqueda y crear alerta</span>
        </button>
      </div>

    </div>
  );
};
