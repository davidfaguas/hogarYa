import React, { useEffect } from 'react';
import { 
  RotateCcw, 
  SearchX, 
  AlertTriangle, 
  RefreshCw, 
  Sparkles, 
  SlidersHorizontal,
  Home,
  ImageOff
} from 'lucide-react';
import { ListingCard } from './ListingCard';
import { NO_PHOTO_FALLBACK_PROPERTY } from '../data/mockProperties';
import { ViewMode } from '../types/property';

interface LoadingSkeletonCardsProps {
  count?: number;
  durationMs?: number;
  onFinishLoading?: () => void;
  onSkipLoading?: () => void;
  viewMode?: ViewMode;
  isMobile?: boolean;
}

export const LoadingSkeletonCards: React.FC<LoadingSkeletonCardsProps> = ({ 
  count = 8,
  durationMs = 1500,
  onFinishLoading,
  onSkipLoading,
  viewMode = 'grid',
  isMobile = false
}) => {
  // Manejo de carga transitoria y ciclo de vida: se inicia el setTimeout al montar y se limpia al desmontar
  useEffect(() => {
    if (!onFinishLoading) return;
    const timer = setTimeout(() => {
      onFinishLoading();
    }, durationMs);

    return () => {
      clearTimeout(timer);
    };
  }, [durationMs, onFinishLoading]);

  return (
    <div className="space-y-4">
      {/* Informative banner explaining Skeleton UX (Non-blocking, strictly in results area) */}
      <div className="bg-teal-50/90 border border-teal-200 rounded-xl p-3 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs text-teal-900 shadow-2xs">
        <div className="flex items-center gap-2.5">
          <span className="relative flex h-2.5 w-2.5 shrink-0">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-teal-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-teal-600"></span>
          </span>
          <div>
            <span className="font-bold">Cargando propiedades...</span>{' '}
            <span className="text-teal-700">
              Estado transitorio de resultados (la navegación y filtros siguen 100% activos).
            </span>
          </div>
        </div>
        {(onSkipLoading || onFinishLoading) && (
          <button
            type="button"
            onClick={onSkipLoading || onFinishLoading}
            className="self-start sm:self-auto px-3 py-1.5 bg-teal-700 hover:bg-teal-800 active:scale-95 text-white font-semibold rounded-lg transition-all cursor-pointer text-[11px]"
          >
            Ver casas de inmediato
          </button>
        )}
      </div>

      {viewMode === 'list' ? (
        <div className="space-y-4 animate-pulse">
          {Array.from({ length: Math.min(count, 5) }).map((_, i) => (
            <div key={i} className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-xs flex flex-col md:flex-row p-3 gap-4">
              <div className="w-full md:w-72 aspect-[16/10] md:aspect-auto md:h-44 bg-slate-200 rounded-xl relative shrink-0">
                <div className="absolute top-2.5 left-2.5 w-16 h-5 bg-slate-300 rounded-full" />
              </div>
              <div className="flex-1 flex flex-col justify-between py-1 space-y-3">
                <div className="space-y-2">
                  <div className="w-1/4 h-3.5 bg-slate-200 rounded" />
                  <div className="w-3/4 h-5 bg-slate-300 rounded" />
                  <div className="w-1/3 h-6 bg-slate-300 rounded" />
                  <div className="w-full h-8 bg-slate-100 rounded-lg mt-2" />
                </div>
                <div className="flex items-center gap-2 pt-2 border-t border-slate-100">
                  <div className="w-28 h-8 bg-slate-200 rounded-xl" />
                  <div className="w-24 h-8 bg-slate-200 rounded-xl" />
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className={`grid gap-4 animate-pulse ${
          viewMode === 'split' 
            ? (isMobile ? 'grid-cols-1' : 'grid-cols-1 sm:grid-cols-2')
            : (isMobile ? 'grid-cols-1' : 'grid-cols-1 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4')
        }`}>
          {Array.from({ length: viewMode === 'split' ? 4 : count }).map((_, i) => (
            <div key={i} className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-xs flex flex-col">
              {/* Skeleton photo */}
              <div className="aspect-[16/10] bg-slate-200 relative">
                <div className="absolute top-2.5 left-2.5 w-16 h-5 bg-slate-300 rounded-full" />
                <div className="absolute top-2.5 right-2.5 w-8 h-8 bg-slate-300 rounded-full" />
              </div>
              
              {/* Skeleton content */}
              <div className="p-4 space-y-3 flex-1 flex flex-col justify-between">
                <div className="space-y-2">
                  <div className="w-1/2 h-3.5 bg-slate-200 rounded" />
                  <div className="w-4/5 h-5 bg-slate-300 rounded" />
                  <div className="w-1/3 h-6 bg-slate-300 rounded" />
                  
                  {/* Fake specs strip */}
                  <div className="h-10 bg-slate-100 rounded-xl mt-2" />
                  <div className="w-full h-7 bg-slate-100 rounded-lg mt-2" />
                </div>

                <div className="pt-2 border-t border-slate-100 flex items-center gap-2">
                  <div className="w-1/2 h-9 bg-slate-200 rounded-xl" />
                  <div className="w-1/2 h-9 bg-slate-200 rounded-xl" />
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

interface EmptyResultsStateProps {
  onResetFilters: () => void;
  onRelaxPriceFilter?: () => void;
  onRelaxBedrooms?: () => void;
}

export const EmptyResultsState: React.FC<EmptyResultsStateProps> = ({
  onResetFilters,
  onRelaxPriceFilter,
  onRelaxBedrooms
}) => {
  return (
    <div className="bg-white rounded-2xl border border-slate-200 p-8 sm:p-12 text-center shadow-xs max-w-2xl mx-auto my-6 animate-in fade-in">
      <div className="w-16 h-16 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center mx-auto mb-4 border border-amber-200">
        <SearchX className="w-8 h-8" />
      </div>

      <h2 className="text-lg sm:text-xl font-bold text-slate-900 mb-2">
        No encontramos casas con estos filtros exactos
      </h2>
      <p className="text-xs sm:text-sm text-slate-500 mb-6 max-w-md mx-auto leading-relaxed">
        Intenta relajar los criterios de búsqueda, ampliar el rango de precio en UF o buscar en comunas aledañas de la Región Metropolitana.
      </p>

      {/* Suggested quick actions */}
      <div className="flex flex-wrap items-center justify-center gap-2 mb-6">
        <button
          onClick={onRelaxPriceFilter || onResetFilters}
          className="px-3 py-1.5 rounded-lg border border-slate-200 text-xs font-semibold text-slate-700 hover:bg-slate-50 hover:border-slate-300"
        >
          Ampliar rango de precio UF
        </button>
        <button
          onClick={onRelaxBedrooms || onResetFilters}
          className="px-3 py-1.5 rounded-lg border border-slate-200 text-xs font-semibold text-slate-700 hover:bg-slate-50 hover:border-slate-300"
        >
          Cualquier número de dormitorios
        </button>
      </div>

      <button
        type="button"
        onClick={onResetFilters}
        className="px-6 py-3 bg-[#0d5c56] hover:bg-[#083d39] text-white text-xs sm:text-sm font-bold rounded-xl shadow-md shadow-[#0d5c56]/20 transition-all inline-flex items-center gap-2"
      >
        <RotateCcw className="w-4 h-4" />
        <span>Restablecer todos los filtros</span>
      </button>
    </div>
  );
};

interface ErrorStateProps {
  onRetry: () => void;
  errorMessage?: string;
}

export const ErrorState: React.FC<ErrorStateProps> = ({
  onRetry,
  errorMessage = 'Ocurrió un problema de conexión al consultar el servidor inmobiliario.'
}) => {
  return (
    <div className="bg-white rounded-2xl border border-rose-200 p-8 sm:p-12 text-center shadow-xs max-w-xl mx-auto my-6 animate-in fade-in">
      <div className="w-16 h-16 rounded-2xl bg-rose-50 text-rose-600 flex items-center justify-center mx-auto mb-4 border border-rose-200">
        <AlertTriangle className="w-8 h-8" />
      </div>

      <h2 className="text-lg sm:text-xl font-bold text-slate-900 mb-2">
        Error al cargar las propiedades
      </h2>
      <p className="text-xs sm:text-sm text-slate-500 mb-6 leading-relaxed">
        {errorMessage}
      </p>

      <button
        type="button"
        onClick={onRetry}
        className="px-6 py-3 bg-[#e35238] hover:bg-[#c84128] text-white text-xs sm:text-sm font-bold rounded-xl shadow-md shadow-[#e35238]/20 transition-all inline-flex items-center gap-2"
      >
        <RefreshCw className="w-4 h-4" />
        <span>Reintentar búsqueda</span>
      </button>
    </div>
  );
};

interface NoPhotoFallbackShowcaseProps {
  onContactClick: () => void;
  onWhatsAppClick: () => void;
}

export const NoPhotoFallbackShowcase: React.FC<NoPhotoFallbackShowcaseProps> = ({
  onContactClick,
  onWhatsAppClick
}) => {
  return (
    <div className="space-y-4 my-4 animate-in fade-in">
      <div className="p-4 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-900 flex items-center gap-2">
        <ImageOff className="w-5 h-5 text-amber-700 shrink-0" />
        <div>
          <strong className="font-bold">Demostración: Estado de tarjeta sin fotografía (Fallback)</strong>
          <p className="text-[11px] text-amber-700 mt-0.5">
            Muestra cómo la plataforma maneja con elegancia las propiedades recién ingresadas antes de que la corredora suba su reportaje fotográfico.
          </p>
        </div>
      </div>

      <div className="max-w-md mx-auto">
        <ListingCard
          property={NO_PHOTO_FALLBACK_PROPERTY}
          isFavorite={false}
          onToggleFavorite={() => {}}
          isCompared={false}
          onToggleCompare={() => {}}
          canCompare={true}
          priceDisplayMode="uf"
          onCotizarClick={onContactClick}
          onVerMasClick={onContactClick}
          onWhatsAppClick={onWhatsAppClick}
          onSimulateMortgage={() => {}}
        />
      </div>
    </div>
  );
};
