import React from 'react';
import { ViewportMode, AppStateMode } from '../types/property';
import { 
  Monitor, 
  Smartphone, 
  Maximize2, 
  CheckCircle2, 
  Loader2, 
  SearchX, 
  AlertTriangle, 
  ImageOff,
  Eye
} from 'lucide-react';

interface ViewportToggleBarProps {
  viewportMode: ViewportMode;
  onViewportModeChange: (mode: ViewportMode) => void;
  appState: AppStateMode;
  onAppStateChange: (state: AppStateMode) => void;
  isCollapsed?: boolean;
  onToggleCollapse?: () => void;
}

export const ViewportToggleBar: React.FC<ViewportToggleBarProps> = ({
  viewportMode,
  onViewportModeChange,
  appState,
  onAppStateChange,
  isCollapsed = false,
  onToggleCollapse
}) => {
  if (isCollapsed) {
    return (
      <div className="fixed top-2 right-2 z-50">
        <button
          type="button"
          onClick={onToggleCollapse}
          className="flex items-center gap-1.5 px-2.5 py-1.5 bg-slate-900/90 hover:bg-slate-900 text-slate-200 hover:text-white rounded-lg border border-slate-700 text-[11px] font-semibold shadow-lg backdrop-blur-xs transition-all cursor-pointer"
          title="Mostrar barra de auditoría de diseño y vistas móviles"
        >
          <Eye className="w-3.5 h-3.5 text-teal-400" />
          <span>Panel de Diseño & Vistas</span>
        </button>
      </div>
    );
  }

  return (
    <aside aria-label="Control de visualización" className="bg-[#0f172a] text-white px-4 py-2 text-xs border-b border-slate-800 shadow-md sticky top-0 z-50">
      <div className="max-w-[1440px] mx-auto flex flex-wrap items-center justify-between gap-3">
        
        {/* Left: Viewport Switcher (1440px vs 390px vs Responsive) */}
        <div className="flex items-center gap-1.5">
          <span className="text-slate-400 font-semibold mr-1 flex items-center gap-1">
            <Eye className="w-3.5 h-3.5 text-[#8ed2ca]" />
            <span className="hidden sm:inline">Vista de Diseño:</span>
          </span>

          <div className="flex items-center bg-slate-800/90 p-0.5 rounded-lg border border-slate-700">
            <button
              onClick={() => onViewportModeChange('desktop')}
              className={`flex items-center gap-1 px-2.5 py-1 rounded-md font-bold transition-all ${
                viewportMode === 'desktop'
                  ? 'bg-teal-700 text-white shadow-xs'
                  : 'text-slate-300 hover:text-white'
              }`}
              title="Ver en resolución Desktop estándar (1440px)"
            >
              <Monitor className="w-3.5 h-3.5" />
              <span>Desktop (1440px)</span>
            </button>

            <button
              onClick={() => onViewportModeChange('mobile')}
              className={`flex items-center gap-1 px-2.5 py-1 rounded-md font-bold transition-all ${
                viewportMode === 'mobile'
                  ? 'bg-teal-700 text-white shadow-xs'
                  : 'text-slate-300 hover:text-white'
              }`}
              title="Ver en viewport Móvil (390px iPhone)"
            >
              <Smartphone className="w-3.5 h-3.5" />
              <span>Móvil (390px)</span>
            </button>

            <button
              onClick={() => onViewportModeChange('responsive')}
              className={`flex items-center gap-1 px-2.5 py-1 rounded-md font-bold transition-all ${
                viewportMode === 'responsive'
                  ? 'bg-teal-700 text-white shadow-xs'
                  : 'text-slate-300 hover:text-white'
              }`}
              title="Ajuste fluido a la ventana completa"
            >
              <Maximize2 className="w-3.5 h-3.5" />
              <span className="hidden md:inline">Fluido</span>
            </button>
          </div>
        </div>

        {/* Right: States switcher (Demo / Audit of empty, loading, error states) */}
        <div className="flex items-center gap-1.5 overflow-x-auto py-0.5">
          <span className="text-slate-400 font-semibold text-[11px] mr-1 hidden xl:inline" title="Permite previsualizar cómo reacciona la interfaz en diferentes estados técnicos">
            Demo de estados:
          </span>

          <button
            onClick={() => onAppStateChange('normal')}
            className={`px-2 py-1 rounded-md font-semibold text-[11px] transition-all flex items-center gap-1 cursor-pointer ${
              appState === 'normal'
                ? 'bg-emerald-600 text-white ring-1 ring-emerald-400'
                : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
            }`}
            title="Vista normal con propiedades reales y funcionales"
          >
            <CheckCircle2 className="w-3 h-3" />
            <span>Normal</span>
          </button>

          <button
            onClick={() => onAppStateChange('loading')}
            className={`px-2 py-1 rounded-md font-semibold text-[11px] transition-all flex items-center gap-1 cursor-pointer ${
              appState === 'loading'
                ? 'bg-amber-600 text-white ring-1 ring-amber-400'
                : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
            }`}
            title="Simula 2 segundos de carga inicial con siluetas (Skeleton) y luego vuelve a normal"
          >
            <Loader2 className={`w-3 h-3 ${appState === 'loading' ? 'animate-spin' : ''}`} />
            <span>Cargando (Skeleton)</span>
          </button>

          <button
            onClick={() => onAppStateChange('empty')}
            className={`px-2 py-1 rounded-md font-semibold text-[11px] transition-all flex items-center gap-1 cursor-pointer ${
              appState === 'empty'
                ? 'bg-sky-600 text-white ring-1 ring-sky-400'
                : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
            }`}
            title="Simula búsqueda sin resultados para probar filtros de relajación"
          >
            <SearchX className="w-3 h-3" />
            <span>Sin resultados</span>
          </button>

          <button
            onClick={() => onAppStateChange('error')}
            className={`px-2 py-1 rounded-md font-semibold text-[11px] transition-all flex items-center gap-1 cursor-pointer ${
              appState === 'error'
                ? 'bg-rose-600 text-white ring-1 ring-rose-400'
                : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
            }`}
            title="Simula error de conexión de servidor con botón de reintento"
          >
            <AlertTriangle className="w-3 h-3" />
            <span>Error</span>
          </button>

          <button
            onClick={() => onAppStateChange('no_photo')}
            className={`px-2 py-1 rounded-md font-semibold text-[11px] transition-all flex items-center gap-1 cursor-pointer ${
              appState === 'no_photo'
                ? 'bg-purple-600 text-white ring-1 ring-purple-400'
                : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
            }`}
            title="Simula publicación sin fotografías cargadas"
          >
            <ImageOff className="w-3 h-3" />
            <span>Sin foto</span>
          </button>

          {onToggleCollapse && (
            <button
              type="button"
              onClick={onToggleCollapse}
              className="ml-2 px-2 py-1 text-[11px] text-slate-400 hover:text-white hover:bg-slate-800 rounded-md transition-colors cursor-pointer border border-slate-700/60"
              title="Ocultar esta barra de demostración"
            >
              ✕ Ocultar
            </button>
          )}
        </div>

      </div>
    </aside>
  );
};
