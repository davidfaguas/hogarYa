import React, { useState, useRef, useEffect } from 'react';
import { 
  Search, 
  MapPin, 
  SlidersHorizontal, 
  X, 
  ChevronDown, 
  Bookmark, 
  LayoutGrid, 
  List as ListIcon, 
  Columns,
  Sparkles
} from 'lucide-react';
import { PropertyType, PropertyFilters, ViewMode } from '../types/property';
import { CHILE_COMMUNES } from '../data/mockProperties';
import { SEMANTIC_SEARCH_SUGGESTIONS } from '../utils/semanticSearch';

interface SearchBarProps {
  filters: PropertyFilters;
  searchQuery: string;
  onSearchChange: (query: string) => void;
  onFilterChange: (filters: Partial<PropertyFilters>) => void;
  onOpenFiltersModal: () => void;
  activeFiltersCount: number;
  onPerformSearch?: () => void;
  viewMode: ViewMode;
  onViewModeChange: (mode: ViewMode) => void;
  onOpenSaveSearch?: () => void;
  isMobileSimulated?: boolean;
}

export const PROPERTY_TYPE_OPTIONS: { id: PropertyType | 'todos'; label: string }[] = [
  { id: 'casa', label: 'Casa' },
  { id: 'departamento', label: 'Departamento' },
  { id: 'parcela', label: 'Parcela' },
  { id: 'terreno', label: 'Terreno' },
  { id: 'oficina', label: 'Oficina' },
  { id: 'local', label: 'Local comercial' },
  { id: 'bodega', label: 'Bodega' },
  { id: 'estacionamiento', label: 'Estacionamiento' },
  { id: 'industrial', label: 'Industrial' },
  { id: 'vacacional', label: 'Vacacional' },
];

export const SearchBar: React.FC<SearchBarProps> = ({
  filters,
  searchQuery,
  onSearchChange,
  onFilterChange,
  onOpenFiltersModal,
  activeFiltersCount,
  onPerformSearch,
  viewMode,
  onViewModeChange,
  onOpenSaveSearch,
  isMobileSimulated = false
}) => {
  const [comunaDropdownOpen, setComunaDropdownOpen] = useState(false);
  const [operationDropdownOpen, setOperationDropdownOpen] = useState(false);
  const [typeDropdownOpen, setTypeDropdownOpen] = useState(false);
  const [inputFocus, setInputFocus] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setComunaDropdownOpen(false);
        setOperationDropdownOpen(false);
        setTypeDropdownOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const matchingCommunes = CHILE_COMMUNES.filter((c) =>
    c.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const handleSelectCommune = (commune: string) => {
    onSearchChange(commune);
    onFilterChange({ comuna: commune });
    setComunaDropdownOpen(false);
  };

  const handleClearCommune = () => {
    onSearchChange('');
    onFilterChange({ comuna: '' });
  };

  return (
    <div 
      ref={containerRef}
      className="bg-white border-b border-slate-200 py-3 transition-shadow"
    >
      <div className="max-w-[1440px] mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Main Search Controls Row */}
        <div className="flex flex-col lg:flex-row items-stretch lg:items-center gap-2">
          
          {/* 1. Selector Comprar / Arrendar */}
          <div className="relative shrink-0">
            <button
              type="button"
              onClick={() => setOperationDropdownOpen(!operationDropdownOpen)}
              className="w-full lg:w-36 flex items-center justify-between px-3.5 py-2.5 bg-white border border-slate-300 rounded-xl text-xs font-bold text-slate-800 hover:border-slate-400 focus:ring-2 focus:ring-[#0d9488]"
            >
              <span className="capitalize">{filters.operation}</span>
              <ChevronDown className="w-4 h-4 text-slate-400" />
            </button>

            {operationDropdownOpen && (
              <div className="absolute top-full left-0 mt-1 w-36 bg-white border border-slate-200 rounded-xl shadow-xl z-50 py-1">
                <button
                  type="button"
                  onClick={() => {
                    onFilterChange({ operation: 'comprar' });
                    setOperationDropdownOpen(false);
                  }}
                  className={`w-full text-left px-3.5 py-2 text-xs font-semibold ${
                    filters.operation === 'comprar' ? 'bg-teal-50 text-[#0d9488]' : 'text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  Comprar
                </button>
                <button
                  type="button"
                  onClick={() => {
                    onFilterChange({ operation: 'arrendar' });
                    setOperationDropdownOpen(false);
                  }}
                  className={`w-full text-left px-3.5 py-2 text-xs font-semibold ${
                    filters.operation === 'arrendar' ? 'bg-teal-50 text-[#0d9488]' : 'text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  Arrendar
                </button>
              </div>
            )}
          </div>

          {/* 2. Selector Tipo de propiedad */}
          <div className="relative shrink-0">
            <button
              type="button"
              onClick={() => setTypeDropdownOpen(!typeDropdownOpen)}
              className="w-full lg:w-44 flex items-center justify-between px-3.5 py-2.5 bg-white border border-slate-300 rounded-xl text-xs font-bold text-slate-800 hover:border-slate-400 focus:ring-2 focus:ring-[#0d9488]"
            >
              <span className="truncate">
                {PROPERTY_TYPE_OPTIONS.find((t) => t.id === filters.propertyType)?.label || 'Tipo de propiedad'}
              </span>
              <ChevronDown className="w-4 h-4 text-slate-400" />
            </button>

            {typeDropdownOpen && (
              <div className="absolute top-full left-0 mt-1 w-48 bg-white border border-slate-200 rounded-xl shadow-xl z-50 py-1 max-h-60 overflow-y-auto">
                {PROPERTY_TYPE_OPTIONS.map((type) => (
                  <button
                    key={type.id}
                    type="button"
                    onClick={() => {
                      onFilterChange({ propertyType: type.id });
                      setTypeDropdownOpen(false);
                    }}
                    className={`w-full text-left px-3.5 py-2 text-xs ${
                      filters.propertyType === type.id
                        ? 'bg-teal-50 text-[#0d9488] font-bold'
                        : 'text-slate-700 hover:bg-slate-50 font-medium'
                    }`}
                  >
                    {type.label}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* 3. Input Comuna o ciudad */}
          <div className="relative flex-1">
            <div className={`relative flex items-center rounded-xl border transition-all ${
              inputFocus 
                ? 'border-[#0d9488] ring-2 ring-[#0d9488]/15 bg-white' 
                : 'border-slate-300 bg-white hover:border-slate-400'
            }`}>
              <div className="pl-3.5 text-slate-400">
                <MapPin className="w-4 h-4 text-[#0d9488]" />
              </div>
              <input
                ref={inputRef}
                type="text"
                value={searchQuery}
                onChange={(e) => {
                  onSearchChange(e.target.value);
                  setComunaDropdownOpen(true);
                }}
                onFocus={() => {
                  setInputFocus(true);
                  setComunaDropdownOpen(true);
                }}
                onBlur={() => {
                  setInputFocus(false);
                  setTimeout(() => setComunaDropdownOpen(false), 200);
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    setComunaDropdownOpen(false);
                    onPerformSearch?.();
                  }
                }}
                placeholder="Busca libremente: ej. 'casa con piscina', 'cerca del metro' o comuna..."
                className="w-full px-3 py-2.5 text-xs text-slate-900 placeholder:text-slate-400 bg-transparent focus:outline-none"
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={handleClearCommune}
                  className="pr-3 text-slate-400 hover:text-slate-600 focus:outline-none cursor-pointer"
                  title="Borrar búsqueda"
                >
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>

            {/* Chilean Location & Semantic Autocomplete Dropdown */}
            {comunaDropdownOpen && (
              <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-slate-200 rounded-xl shadow-xl z-50 overflow-hidden divide-y divide-slate-100 max-h-80 overflow-y-auto">
                {/* 1. Semantic Suggestions */}
                <div className="p-2.5 bg-teal-50/70 border-b border-teal-100">
                  <div className="flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-wider text-teal-800 mb-1.5">
                    <Sparkles className="w-3 h-3 text-teal-600" />
                    <span>Filtros semánticos recomendados</span>
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {SEMANTIC_SEARCH_SUGGESTIONS.map((sug) => (
                      <button
                        key={sug}
                        type="button"
                        onMouseDown={(e) => {
                          e.preventDefault();
                          onSearchChange(sug);
                          setComunaDropdownOpen(false);
                          onPerformSearch?.();
                        }}
                        className="px-2.5 py-1 rounded-lg text-xs font-semibold bg-white border border-teal-200 text-teal-800 hover:bg-teal-600 hover:text-white transition-all cursor-pointer shadow-2xs"
                      >
                        "{sug}"
                      </button>
                    ))}
                  </div>
                </div>

                {/* 2. Chilean Communes */}
                <div>
                  <div className="px-3.5 py-1.5 bg-slate-50 text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    Comunas en Chile
                  </div>
                  {matchingCommunes.slice(0, 7).map((c) => (
                    <button
                      key={c}
                      type="button"
                      onMouseDown={(e) => {
                        e.preventDefault();
                        handleSelectCommune(c);
                      }}
                      className="w-full flex items-center justify-between px-3.5 py-2 text-xs text-left text-slate-800 hover:bg-teal-50 hover:text-[#0d9488] transition-colors cursor-pointer"
                    >
                      <div className="flex items-center gap-2">
                        <MapPin className="w-3.5 h-3.5 text-slate-400" />
                        <span className="font-semibold">{c}</span>
                      </div>
                      <span className="text-[10px] text-slate-400">Región Metropolitana</span>
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* 4. Action Buttons */}
          <div className="flex items-center gap-2 shrink-0">
            
            {/* Buscar Button */}
            <button
              type="button"
              onClick={() => {
                setComunaDropdownOpen(false);
                onPerformSearch?.();
              }}
              className="flex-1 lg:flex-initial flex items-center justify-center gap-1.5 px-5 py-2.5 bg-[#0d9488] hover:bg-[#0f766e] active:scale-95 text-white font-bold text-xs rounded-xl shadow-sm transition-all focus:ring-2 focus:ring-[#0d9488] cursor-pointer"
            >
              <Search className="w-4 h-4 stroke-[2.5]" />
              <span>Buscar</span>
            </button>

            {/* "Más filtros" Button */}
            <button
              type="button"
              onClick={onOpenFiltersModal}
              className={`flex items-center justify-center gap-1.5 px-3.5 py-2.5 rounded-xl border text-xs font-bold transition-all cursor-pointer ${
                activeFiltersCount > 0
                  ? 'border-[#0d9488] bg-teal-50 text-[#0d9488]'
                  : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
              }`}
            >
              <SlidersHorizontal className="w-4 h-4 text-[#0d9488]" />
              <span>Más filtros</span>
              {activeFiltersCount > 0 && (
                <span className="w-5 h-5 rounded-full bg-[#0d9488] text-white text-[10px] font-bold flex items-center justify-center">
                  {activeFiltersCount}
                </span>
              )}
            </button>

            {/* View Mode Toggle Icons */}
            {!isMobileSimulated && (
              <div className="hidden sm:flex items-center bg-slate-100 p-0.5 rounded-xl border border-slate-200">
                <button
                  type="button"
                  onClick={() => onViewModeChange('list')}
                  className={`p-2 rounded-lg transition-colors cursor-pointer ${
                    viewMode === 'list' ? 'bg-[#0f172a] text-white shadow-xs' : 'text-slate-600 hover:text-slate-900'
                  }`}
                  title="Vista Lista"
                >
                  <ListIcon className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  onClick={() => onViewModeChange('grid')}
                  className={`p-2 rounded-lg transition-colors cursor-pointer ${
                    viewMode === 'grid' ? 'bg-[#0f172a] text-white shadow-xs' : 'text-slate-600 hover:text-slate-900'
                  }`}
                  title="Vista Grilla"
                >
                  <LayoutGrid className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  onClick={() => onViewModeChange('split')}
                  className={`p-2 rounded-lg transition-colors cursor-pointer ${
                    viewMode === 'split' ? 'bg-[#0f172a] text-white shadow-xs' : 'text-slate-600 hover:text-slate-900'
                  }`}
                  title="Vista Dividida (Lista + Mapa)"
                >
                  <Columns className="w-4 h-4" />
                </button>
              </div>
            )}

            {/* "Guardar" Button */}
            <button
              type="button"
              onClick={onOpenSaveSearch}
              className="hidden sm:flex items-center gap-1.5 px-3 py-2.5 rounded-xl border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 text-xs font-bold transition-colors cursor-pointer"
              title="Guardar búsqueda para recibir alertas"
            >
              <Bookmark className="w-4 h-4 text-slate-500" />
              <span>Guardar</span>
            </button>

          </div>

        </div>

        {/* Active chips row */}
        <div className="flex flex-wrap items-center gap-2 mt-3 pt-2 border-t border-slate-100">
          <span className="text-[11px] font-semibold text-slate-400">
            Filtros activos:
          </span>

          <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-bold bg-teal-50 text-[#0f766e] border border-teal-200/60">
            <span className="capitalize">{filters.operation}</span>
          </span>

          <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-bold bg-teal-50 text-[#0f766e] border border-teal-200/60">
            <span>{PROPERTY_TYPE_OPTIONS.find((t) => t.id === filters.propertyType)?.label}</span>
          </span>

          {filters.comuna && (
            <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-bold bg-teal-50 text-[#0f766e] border border-teal-200/60">
              <span>{filters.comuna}</span>
              <button 
                type="button"
                onClick={handleClearCommune}
                className="hover:text-red-500 cursor-pointer"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </span>
          )}

          {filters.dormitorios !== null && (
            <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-bold bg-teal-50 text-[#0f766e] border border-teal-200/60">
              <span>{filters.dormitorios}+ Dorms</span>
              <button 
                type="button"
                onClick={() => onFilterChange({ dormitorios: null })}
                className="hover:text-red-500 cursor-pointer"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </span>
          )}

          {filters.estado !== 'todas' && (
            <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-bold bg-teal-50 text-[#0f766e] border border-teal-200/60">
              <span className="capitalize">{filters.estado}</span>
              <button 
                type="button"
                onClick={() => onFilterChange({ estado: 'todas' })}
                className="hover:text-red-500 cursor-pointer"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </span>
          )}

          {searchQuery && (
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-teal-600 text-white shadow-2xs">
              <Sparkles className="w-3 h-3 text-teal-200 shrink-0" />
              <span>Búsqueda: "{searchQuery}"</span>
              <button 
                type="button"
                onClick={handleClearCommune}
                className="hover:text-teal-200 cursor-pointer ml-0.5"
                title="Quitar búsqueda"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </span>
          )}
        </div>

        {/* Quick Semantic Query Suggestions Pill Bar */}
        <div className="flex items-center flex-wrap gap-1.5 mt-2.5 pt-2 border-t border-slate-100">
          <span className="text-[11px] font-bold text-slate-500 flex items-center gap-1 mr-1">
            <Sparkles className="w-3 h-3 text-teal-600" />
            <span>Filtros semánticos rápidos:</span>
          </span>
          {SEMANTIC_SEARCH_SUGGESTIONS.map((sug) => (
            <button
              key={sug}
              type="button"
              onClick={() => {
                onSearchChange(sug);
                onPerformSearch?.();
              }}
              className={`px-2.5 py-0.5 rounded-full text-[11px] font-medium transition-all cursor-pointer ${
                searchQuery.toLowerCase() === sug.toLowerCase()
                  ? 'bg-teal-700 text-white shadow-2xs font-bold'
                  : 'bg-slate-100 text-slate-700 hover:bg-teal-50 hover:text-teal-800 hover:border-teal-300 border border-slate-200/80'
              }`}
            >
              {sug}
            </button>
          ))}
        </div>

      </div>
    </div>
  );
};
