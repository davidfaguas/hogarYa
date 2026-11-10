import React, { useEffect, useState } from 'react';
import { Check, ArrowRight, RotateCcw } from 'lucide-react';
import { PropertyFilters, PropertyType } from '../types/property';
import { CHILE_REGIONS } from '../data/mockProperties';

interface FilterBottomSheetProps {
  isOpen: boolean;
  onClose: () => void;
  filters: PropertyFilters;
  onFilterChange: (updated: Partial<PropertyFilters>) => void;
  onResetFilters: () => void;
  onOpenSaveAlertModal: () => void;
  resultsCount: number;
}

export const FilterBottomSheet: React.FC<FilterBottomSheetProps> = ({
  isOpen,
  onClose,
  filters,
  onFilterChange,
  onResetFilters,
  resultsCount
}) => {
  const [currencyMode, setCurrencyMode] = useState<'uf' | 'clp'>('uf');
  const [tempMinPrice, setTempMinPrice] = useState(filters.priceMinUf?.toString() || '');
  const [tempMaxPrice, setTempMaxPrice] = useState(filters.priceMaxUf?.toString() || '');

  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
      setTempMinPrice(filters.priceMinUf?.toString() || '');
      setTempMaxPrice(filters.priceMaxUf?.toString() || '');
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen, filters.priceMinUf, filters.priceMaxUf]);

  if (!isOpen) return null;

  const appliedFiltersCount = [
    filters.comuna,
    filters.region && filters.region !== 'Todas las regiones',
    filters.estado !== 'todas',
    filters.dormitorios !== null,
    filters.banos !== null,
    filters.priceMinUf !== null || filters.priceMaxUf !== null,
    filters.m2Min !== null || filters.m2Max !== null,
    filters.tourVirtualOnly,
    filters.videoOnly,
    filters.subsidioOnly
  ].filter(Boolean).length;

  const handleApplyPrice = () => {
    onFilterChange({
      priceMinUf: tempMinPrice ? Number(tempMinPrice) : null,
      priceMaxUf: tempMaxPrice ? Number(tempMaxPrice) : null
    });
  };

  const propertyTypes: { id: PropertyType; label: string }[] = [
    { id: 'casa', label: 'Casa' },
    { id: 'departamento', label: 'Departamento' },
    { id: 'oficina', label: 'Oficina' },
    { id: 'local', label: 'Local comercial' },
    { id: 'bodega', label: 'Bodega' },
    { id: 'estacionamiento', label: 'Estacionamiento' },
    { id: 'parcela', label: 'Parcela' },
    { id: 'terreno', label: 'Terreno' },
    { id: 'industrial', label: 'Industrial' },
    { id: 'vacacional', label: 'Vacacional' },
  ];

  return (
    <div 
      className="fixed inset-0 z-50 flex justify-end bg-black/50 backdrop-blur-2xs transition-opacity animate-in fade-in duration-200"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
    >
      {/* Slide-over Drawer from Right */}
      <div 
        className="relative w-full max-w-md sm:max-w-lg bg-white h-full shadow-2xl flex flex-col z-10 animate-in slide-in-from-right duration-300"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Top Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-white">
          <h2 className="text-xl font-bold text-slate-900">
            Filtros
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="w-8 h-8 rounded-full bg-slate-100 hover:bg-slate-200 text-slate-700 flex items-center justify-center transition-colors font-bold text-sm cursor-pointer"
            aria-label="Cerrar filtros"
          >
            ✕
          </button>
        </div>

        {/* Applied Filters Counter & Reset */}
        <div className="px-6 py-3 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
          <span className="text-xs font-bold text-slate-700">
            Filtros aplicados ({appliedFiltersCount})
          </span>
          <button
            type="button"
            onClick={onResetFilters}
            className="text-xs font-semibold text-[#0d9488] hover:underline flex items-center gap-1 cursor-pointer"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Limpiar filtros</span>
          </button>
        </div>

        {/* Scrollable Filter Options Body */}
        <div className="overflow-y-auto px-6 py-5 flex-1 space-y-6 text-xs text-slate-800">
          
          {/* 1. Tipo de búsqueda */}
          <div>
            <h3 className="text-xs font-bold text-slate-900 mb-2">
              Tipo de búsqueda
            </h3>
            <div className="space-y-2">
              {[
                { id: 'comprar', label: 'Comprar' },
                { id: 'arrendar', label: 'Arrendar' }
              ].map((op) => (
                <label key={op.id} className="flex items-center gap-2.5 cursor-pointer">
                  <input
                    type="radio"
                    name="operation_type"
                    checked={filters.operation === op.id}
                    onChange={() => onFilterChange({ operation: op.id as any })}
                    className="w-4 h-4 text-[#0d9488] border-slate-300 focus:ring-[#0d9488]"
                  />
                  <span className="font-semibold text-slate-800">{op.label}</span>
                </label>
              ))}
            </div>
          </div>

          {/* 2. Estado (Nueva / Usada) */}
          <div>
            <h3 className="text-xs font-bold text-slate-900 mb-2">
              Estado
            </h3>
            <div className="space-y-2">
              <label className="flex items-center gap-2.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={filters.estado === 'nueva'}
                  onChange={(e) => onFilterChange({ estado: e.target.checked ? 'nueva' : 'todas' })}
                  className="w-4 h-4 rounded text-[#0d9488] border-slate-300 focus:ring-[#0d9488]"
                />
                <span className="font-semibold text-slate-800">Nueva</span>
              </label>
              <label className="flex items-center gap-2.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={filters.estado === 'usada'}
                  onChange={(e) => onFilterChange({ estado: e.target.checked ? 'usada' : 'todas' })}
                  className="w-4 h-4 rounded text-[#0d9488] border-slate-300 focus:ring-[#0d9488]"
                />
                <span className="font-semibold text-slate-800">Usada</span>
              </label>
            </div>
          </div>

          {/* 3. Región */}
          <div>
            <h3 className="text-xs font-bold text-slate-900 mb-1.5">
              Región
            </h3>
            <select
              value={filters.region}
              onChange={(e) => onFilterChange({ region: e.target.value })}
              className="w-full text-xs bg-white border border-slate-300 rounded-xl px-3 py-2.5 focus:ring-2 focus:ring-[#0d9488] outline-none text-slate-800"
            >
              {CHILE_REGIONS.map((r) => (
                <option key={r} value={r === 'Todas las regiones' ? '' : r}>
                  {r}
                </option>
              ))}
            </select>
          </div>

          {/* 4. Tipo de propiedad */}
          <div>
            <h3 className="text-xs font-bold text-slate-900 mb-2">
              Tipo de propiedad
            </h3>
            <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
              {propertyTypes.map((t) => (
                <label key={t.id} className="flex items-center gap-2.5 cursor-pointer">
                  <input
                    type="radio"
                    name="prop_type"
                    checked={filters.propertyType === t.id}
                    onChange={() => onFilterChange({ propertyType: t.id })}
                    className="w-4 h-4 text-[#0d9488] border-slate-300 focus:ring-[#0d9488]"
                  />
                  <span className="font-medium text-slate-800">{t.label}</span>
                </label>
              ))}
            </div>
          </div>

          {/* 5. Dormitorios */}
          <div>
            <h3 className="text-xs font-bold text-slate-900 mb-2">
              Dormitorios
            </h3>
            <div className="flex flex-wrap gap-1.5">
              {[
                { label: 'Cualquiera', value: null },
                { label: '1', value: 1 },
                { label: '2', value: 2 },
                { label: '3', value: 3 },
                { label: '4+', value: 4 }
              ].map((opt) => (
                <button
                  key={opt.label}
                  type="button"
                  onClick={() => onFilterChange({ dormitorios: opt.value })}
                  className={`px-3 py-1.5 rounded-lg font-semibold border transition-all cursor-pointer ${
                    filters.dormitorios === opt.value
                      ? 'bg-[#0f172a] text-white border-[#0f172a]'
                      : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-50'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* 6. Baños */}
          <div>
            <h3 className="text-xs font-bold text-slate-900 mb-2">
              Baños
            </h3>
            <div className="flex flex-wrap gap-1.5">
              {[
                { label: 'Cualquiera', value: null },
                { label: '1+', value: 1 },
                { label: '2+', value: 2 },
                { label: '3+', value: 3 },
                { label: '4+', value: 4 }
              ].map((opt) => (
                <button
                  key={opt.label}
                  type="button"
                  onClick={() => onFilterChange({ banos: opt.value })}
                  className={`px-3 py-1.5 rounded-lg font-semibold border transition-all cursor-pointer ${
                    filters.banos === opt.value
                      ? 'bg-[#0f172a] text-white border-[#0f172a]'
                      : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-50'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* 7. Precio con switch $ / UF */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-xs font-bold text-slate-900">
                Precio
              </h3>
              <div className="flex items-center bg-slate-100 rounded-lg p-0.5 border border-slate-200">
                <button
                  type="button"
                  onClick={() => setCurrencyMode('clp')}
                  className={`px-2 py-0.5 rounded text-[11px] font-bold transition-all cursor-pointer ${
                    currencyMode === 'clp' ? 'bg-white text-[#0d9488] shadow-2xs' : 'text-slate-500'
                  }`}
                >
                  $
                </button>
                <button
                  type="button"
                  onClick={() => setCurrencyMode('uf')}
                  className={`px-2 py-0.5 rounded text-[11px] font-bold transition-all cursor-pointer ${
                    currencyMode === 'uf' ? 'bg-[#0d9488] text-white shadow-2xs' : 'text-slate-500'
                  }`}
                >
                  UF
                </button>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <input
                type="number"
                placeholder="Desde"
                value={tempMinPrice}
                onChange={(e) => setTempMinPrice(e.target.value)}
                className="w-full text-xs bg-white border border-slate-300 rounded-lg px-2.5 py-2 focus:ring-1 focus:ring-[#0d9488] outline-none"
              />
              <span className="text-slate-400">-</span>
              <input
                type="number"
                placeholder="Hasta"
                value={tempMaxPrice}
                onChange={(e) => setTempMaxPrice(e.target.value)}
                className="w-full text-xs bg-white border border-slate-300 rounded-lg px-2.5 py-2 focus:ring-1 focus:ring-[#0d9488] outline-none"
              />
              <button
                type="button"
                onClick={handleApplyPrice}
                className="w-8 h-8 rounded-lg bg-[#0d9488] hover:bg-[#0f766e] text-white flex items-center justify-center shrink-0 cursor-pointer"
                aria-label="Aplicar rango de precio"
              >
                <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* 8. Superficie útil */}
          <div>
            <h3 className="text-xs font-bold text-slate-900 mb-2">
              Superficie útil (m²)
            </h3>
            <div className="flex items-center gap-2">
              <input
                type="number"
                placeholder="Min m²"
                value={filters.m2Min || ''}
                onChange={(e) => onFilterChange({ m2Min: e.target.value ? Number(e.target.value) : null })}
                className="w-full text-xs bg-white border border-slate-300 rounded-lg px-2.5 py-2 focus:ring-1 focus:ring-[#0d9488] outline-none"
              />
              <span className="text-slate-400">-</span>
              <input
                type="number"
                placeholder="Max m²"
                value={filters.m2Max || ''}
                onChange={(e) => onFilterChange({ m2Max: e.target.value ? Number(e.target.value) : null })}
                className="w-full text-xs bg-white border border-slate-300 rounded-lg px-2.5 py-2 focus:ring-1 focus:ring-[#0d9488] outline-none"
              />
            </div>
          </div>

          {/* 9. Multimedia */}
          <div>
            <h3 className="text-xs font-bold text-slate-900 mb-2">
              Multimedia
            </h3>
            <div className="space-y-2">
              <label className="flex items-center gap-2.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={filters.tourVirtualOnly}
                  onChange={(e) => onFilterChange({ tourVirtualOnly: e.target.checked })}
                  className="w-4 h-4 rounded text-[#0d9488] border-slate-300 focus:ring-[#0d9488]"
                />
                <span className="font-medium text-slate-800">Tour virtual 360°</span>
              </label>
              <label className="flex items-center gap-2.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={filters.videoOnly}
                  onChange={(e) => onFilterChange({ videoOnly: e.target.checked })}
                  className="w-4 h-4 rounded text-[#0d9488] border-slate-300 focus:ring-[#0d9488]"
                />
                <span className="font-medium text-slate-800">Video</span>
              </label>
            </div>
          </div>

        </div>

        {/* Sticky Footer CTA */}
        <div className="p-4 bg-white border-t border-slate-200">
          <button
            type="button"
            onClick={onClose}
            className="w-full py-3 bg-[#0d9488] hover:bg-[#0f766e] text-white font-extrabold text-sm rounded-xl shadow-md transition-all flex items-center justify-center gap-2 cursor-pointer"
          >
            <Check className="w-4 h-4" />
            <span>Ver {resultsCount.toLocaleString('es-CL')} propiedades</span>
          </button>
        </div>

      </div>
    </div>
  );
};
