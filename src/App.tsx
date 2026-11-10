import React, { useState, useMemo, useEffect } from 'react';
import { 
  Property, 
  PropertyFilters, 
  SortOption, 
  ViewMode, 
  AppStateMode, 
  ViewportMode 
} from './types/property';
import { MOCK_PROPERTIES } from './data/mockProperties';
import { 
  filterProperties, 
  sortProperties 
} from './utils/realEstateCalculations';
import { filterAndRankProperties } from './utils/semanticSearch';

// Components
import { Header } from './components/Header';
import { SearchBar } from './components/SearchBar';
import { FeaturedProjectsBanner } from './components/FeaturedProjectsBanner';
import { FilterSidebar } from './components/FilterSidebar';
import { FilterBottomSheet } from './components/FilterBottomSheet';
import { ResultsHeader } from './components/ResultsHeader';
import { ListingCard } from './components/ListingCard';
import { InteractiveMap } from './components/InteractiveMap';
import { CompareBar } from './components/CompareBar';
import { CompareModal } from './components/CompareModal';
import { CotizarModal } from './components/CotizarModal';
import { PropertyDetailModal } from './components/PropertyDetailModal';
import { MortgageSimulatorModal } from './components/MortgageSimulatorModal';
import { WhatsAppModal } from './components/WhatsAppModal';
import { SaveSearchModal } from './components/SaveSearchModal';
import { BelowResultsSection } from './components/BelowResultsSection';
import { FloatingCurrencyBar } from './components/FloatingCurrencyBar';
import { 
  LoadingSkeletonCards, 
  EmptyResultsState, 
  ErrorState, 
  NoPhotoFallbackShowcase 
} from './components/StateViews';
import { ViewportToggleBar } from './components/ViewportToggleBar';

import { Map as MapIcon, List, Home, ChevronRight } from 'lucide-react';

export default function App() {
  // 1. Core State
  const [viewportMode, setViewportMode] = useState<ViewportMode>('responsive');
  const [appState, setAppState] = useState<AppStateMode>('normal');
  const [isToolbarCollapsed, setIsToolbarCollapsed] = useState(false);
  const [viewMode, setViewMode] = useState<ViewMode>('grid'); // Default grid
  const [priceDisplayMode, setPriceDisplayMode] = useState<'uf' | 'clp'>('uf');
  const [mobileActiveTab, setMobileActiveTab] = useState<'lista' | 'mapa'>('lista');

  const handleAppStateChange = (newState: AppStateMode) => {
    setAppState(newState);
  };

  // Gestión del ciclo de vida del estado transitorio de carga:
  // Se asegura de que setTimeout se ejecute correctamente en el ciclo de vida y se limpie al desmontar o cambiar de estado.
  useEffect(() => {
    if (appState !== 'loading') return;

    const timer = setTimeout(() => {
      setAppState('normal');
    }, 1500);

    return () => {
      clearTimeout(timer);
    };
  }, [appState]);

  // Search & Filter State
  const [searchQuery, setSearchQuery] = useState('');
  const [sortOption, setSortOption] = useState<SortOption>('relevancia');
  const [filters, setFilters] = useState<PropertyFilters>({
    operation: 'comprar',
    propertyType: 'casa',
    comuna: '',
    region: '',
    estado: 'todas',
    dormitorios: null,
    banos: null,
    priceMinUf: null,
    priceMaxUf: null,
    m2Min: null,
    m2Max: null,
    tourVirtualOnly: false,
    videoOnly: false,
    subsidioOnly: false,
    parkingOnly: false,
    verifiedOnly: false
  });

  // User Interactive Collections
  const [favorites, setFavorites] = useState<Set<string>>(new Set(['prop-1', 'prop-5']));
  const [comparedIds, setComparedIds] = useState<Set<string>>(new Set());
  const [hoveredPropertyId, setHoveredPropertyId] = useState<string | null>(null);
  const [selectedMapProperty, setSelectedMapProperty] = useState<Property | null>(null);

  // Modals
  const [isFilterDrawerOpen, setIsFilterDrawerOpen] = useState(false);
  const [isCompareModalOpen, setIsCompareModalOpen] = useState(false);
  const [isSaveSearchModalOpen, setIsSaveSearchModalOpen] = useState(false);
  const [cotizarProperty, setCotizarProperty] = useState<Property | null>(null);
  const [detailProperty, setDetailProperty] = useState<Property | null>(null);
  const [whatsAppProperty, setWhatsAppProperty] = useState<Property | null>(null);
  const [mortgageSimulatorProperty, setMortgageSimulatorProperty] = useState<Property | null>(null);
  const [isMortgageSimulatorOpen, setIsMortgageSimulatorOpen] = useState(false);

  // Filter & Sort Calculation
  // Semantic Search & Structured Filter Calculation
  const filteredProperties = useMemo(() => {
    return filterAndRankProperties(MOCK_PROPERTIES, filters, searchQuery, sortOption);
  }, [filters, searchQuery, sortOption]);

  // Active filters count for badges
  const activeFiltersCount = useMemo(() => {
    let count = 0;
    if (filters.estado !== 'todas') count++;
    if (filters.comuna) count++;
    if (filters.region && filters.region !== 'Todas las regiones') count++;
    if (filters.dormitorios !== null) count++;
    if (filters.banos !== null) count++;
    if (filters.priceMinUf !== null || filters.priceMaxUf !== null) count++;
    if (filters.m2Min !== null || filters.m2Max !== null) count++;
    if (filters.tourVirtualOnly) count++;
    if (filters.videoOnly) count++;
    if (filters.subsidioOnly) count++;
    if (filters.parkingOnly) count++;
    if (filters.verifiedOnly) count++;
    if (searchQuery.trim()) count++;
    return count;
  }, [filters, searchQuery]);

  // Handler functions
  const handleFilterChange = (updated: Partial<PropertyFilters>) => {
    setFilters((prev) => ({ ...prev, ...updated }));
    if (appState === 'loading') {
      setAppState('normal');
    }
  };

  const handleResetFilters = () => {
    if (appState === 'loading') {
      setAppState('normal');
    }
    setFilters({
      operation: 'comprar',
      propertyType: 'casa',
      comuna: '',
      region: '',
      estado: 'todas',
      dormitorios: null,
      banos: null,
      priceMinUf: null,
      priceMaxUf: null,
      m2Min: null,
      m2Max: null,
      tourVirtualOnly: false,
      videoOnly: false,
      subsidioOnly: false,
      parkingOnly: false,
      verifiedOnly: false
    });
    setSearchQuery('');
    setAppState('normal');
  };

  const handleToggleFavorite = (id: string) => {
    setFavorites((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleToggleCompare = (id: string) => {
    setComparedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        if (next.size >= 3) {
          alert('Puedes comparar un máximo de 3 propiedades simultáneamente.');
          return prev;
        }
        next.add(id);
      }
      return next;
    });
  };

  const handleRemoveFromCompare = (id: string) => {
    setComparedIds((prev) => {
      const next = new Set(prev);
      next.delete(id);
      return next;
    });
  };

  const handleClearCompare = () => {
    setComparedIds(new Set());
  };

  const comparedPropertiesList = useMemo(() => {
    return MOCK_PROPERTIES.filter((p) => comparedIds.has(p.id));
  }, [comparedIds]);

  const handleOpenMortgageSimulator = (prop?: Property | null) => {
    setMortgageSimulatorProperty(prop || null);
    setIsMortgageSimulatorOpen(true);
  };

  const toggleCurrencyMode = () => {
    setPriceDisplayMode((m) => (m === 'uf' ? 'clp' : 'uf'));
  };

  // Main application content
  const isMobile = viewportMode === 'mobile';

  const renderAppContent = () => (
    <div className="min-h-screen flex flex-col bg-slate-50 text-slate-900 relative">
      
      {/* 1. Header with HogarYa Brand */}
      <Header
        favoritesCount={favorites.size}
        compareCount={comparedIds.size}
        onOpenCompare={() => setIsCompareModalOpen(true)}
        priceDisplayMode={priceDisplayMode}
        onTogglePriceDisplayMode={toggleCurrencyMode}
        onOpenSavedAlerts={() => setIsSaveSearchModalOpen(true)}
        onOpenMortgageSimulator={() => handleOpenMortgageSimulator(null)}
        onSelectOperation={(op) => handleFilterChange({ operation: op })}
        onSelectPropertyType={(type) => handleFilterChange({ propertyType: type as any })}
        isMobileSimulated={isMobile}
      />

      {/* 2. Breadcrumbs Bar */}
      <div className="bg-white border-b border-slate-200/80 py-2">
        <div className="max-w-[1440px] mx-auto px-4 sm:px-6 lg:px-8 flex items-center gap-2 text-xs text-slate-500 font-medium">
          <a href="#" className="flex items-center gap-1 hover:text-[#0d9488] transition-colors">
            <Home className="w-3.5 h-3.5" />
            <span>HogarYa</span>
          </a>
          <ChevronRight className="w-3 h-3 text-slate-300" />
          <span className="capitalize">{filters.operation === 'comprar' ? 'Venta' : 'Arriendo'}</span>
          <ChevronRight className="w-3 h-3 text-slate-300" />
          <span className="text-slate-900 font-bold capitalize">
            {filters.propertyType}
          </span>
          {filters.comuna && (
            <>
              <ChevronRight className="w-3 h-3 text-slate-300" />
              <span className="text-[#0d9488] font-bold">{filters.comuna}</span>
            </>
          )}
        </div>
      </div>

      {/* 3. Page Title & Count */}
      <div className="max-w-[1440px] mx-auto w-full px-4 sm:px-6 lg:px-8 pt-4 pb-1">
        <h1 className="text-2xl sm:text-3xl font-black text-slate-900 tracking-tight">
          Venta de casas{filters.comuna ? ` en ${filters.comuna}` : ' en Chile'}
        </h1>
        <p className="text-xs sm:text-sm text-slate-500 font-medium mt-0.5">
          28.513 propiedades encontradas para compra directa.
        </p>
      </div>

      {/* 4. Featured Projects Showcase Banners */}
      <div className="max-w-[1440px] mx-auto w-full px-4 sm:px-6 lg:px-8 mt-2">
        <FeaturedProjectsBanner
          onOpenCotizar={setCotizarProperty}
          properties={MOCK_PROPERTIES}
        />
      </div>

      {/* 5. Sticky Search Bar */}
      <div className="sticky top-14 sm:top-16 z-30 shadow-xs">
        <SearchBar
          filters={filters}
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          onFilterChange={handleFilterChange}
          onOpenFiltersModal={() => setIsFilterDrawerOpen(true)}
          activeFiltersCount={activeFiltersCount}
          onPerformSearch={() => {}}
          viewMode={viewMode}
          onViewModeChange={setViewMode}
          onOpenSaveSearch={() => setIsSaveSearchModalOpen(true)}
          isMobileSimulated={isMobile}
        />
      </div>

      {/* 6. Main Body Container */}
      <main className="flex-1 max-w-[1440px] w-full mx-auto px-4 sm:px-6 lg:px-8 py-4">
        
        {/* Results Header: Count, Sort select "Ordenar: Más relevantes v" */}
        <ResultsHeader
          totalCount={MOCK_PROPERTIES.length}
          filteredCount={filteredProperties.length}
          sortOption={sortOption}
          onSortChange={setSortOption}
          viewMode={viewMode}
          onViewModeChange={setViewMode}
          priceDisplayMode={priceDisplayMode}
          onTogglePriceDisplayMode={toggleCurrencyMode}
          onOpenMobileFilters={() => setIsFilterDrawerOpen(true)}
          activeFiltersCount={activeFiltersCount}
          comunaName={filters.comuna}
          isMobileSimulated={isMobile}
          semanticQuery={searchQuery.trim() || undefined}
          onClearSemanticQuery={() => {
            setSearchQuery('');
            handleFilterChange({ comuna: '' });
          }}
        />

        {/* Dynamic States */}
        {appState === 'loading' ? (
          <div className="mt-2">
            {viewMode === 'split' ? (
              <div className={`grid gap-5 ${isMobile ? 'grid-cols-1' : 'grid-cols-1 xl:grid-cols-12'}`}>
                <div className={`${isMobile ? '' : 'xl:col-span-7'} space-y-4`}>
                  <LoadingSkeletonCards 
                    count={4} 
                    viewMode="split"
                    isMobile={isMobile}
                    durationMs={1500}
                    onFinishLoading={() => setAppState('normal')}
                    onSkipLoading={() => setAppState('normal')} 
                  />
                </div>
                <div className={`${isMobile ? 'h-[500px]' : 'xl:col-span-5 sticky top-36 h-[calc(100vh-160px)] min-h-[500px]'} ${
                  mobileActiveTab === 'lista' ? (isMobile ? 'hidden' : 'hidden xl:block') : 'block'
                }`}>
                  <InteractiveMap
                    properties={filteredProperties}
                    hoveredPropertyId={hoveredPropertyId}
                    onHoverProperty={setHoveredPropertyId}
                    selectedProperty={selectedMapProperty}
                    onSelectProperty={(prop) => {
                      setSelectedMapProperty(prop);
                      if (prop) setDetailProperty(prop);
                    }}
                    onContactProperty={setCotizarProperty}
                    priceDisplayMode={priceDisplayMode}
                  />
                </div>
              </div>
            ) : (
              <LoadingSkeletonCards 
                count={8} 
                viewMode={viewMode}
                isMobile={isMobile}
                durationMs={1500}
                onFinishLoading={() => setAppState('normal')}
                onSkipLoading={() => setAppState('normal')} 
              />
            )}
          </div>
        ) : appState === 'error' ? (
          <ErrorState onRetry={() => setAppState('normal')} />
        ) : appState === 'no_photo' ? (
          <NoPhotoFallbackShowcase
            onContactClick={() => setCotizarProperty(MOCK_PROPERTIES[0])}
            onWhatsAppClick={() => setWhatsAppProperty(MOCK_PROPERTIES[0])}
          />
        ) : filteredProperties.length === 0 || appState === 'empty' ? (
          <EmptyResultsState
            onResetFilters={handleResetFilters}
            onRelaxPriceFilter={() => handleFilterChange({ priceMinUf: null, priceMaxUf: null })}
            onRelaxBedrooms={() => handleFilterChange({ dormitorios: null })}
          />
        ) : (
          /* Normal Results Display */
          <div className="mt-2">
            
            {/* View Mode: Grid (Default 4 columns desktop / 2 tablet / 1 mobile) */}
            {viewMode === 'grid' && (
              <div className={`grid gap-4 ${
                isMobile 
                  ? 'grid-cols-1' 
                  : 'grid-cols-1 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4'
              }`}>
                {filteredProperties.map((property) => (
                  <ListingCard
                    key={property.id}
                    property={property}
                    isFavorite={favorites.has(property.id)}
                    onToggleFavorite={handleToggleFavorite}
                    isCompared={comparedIds.has(property.id)}
                    onToggleCompare={handleToggleCompare}
                    canCompare={comparedIds.size < 3}
                    priceDisplayMode={priceDisplayMode}
                    onTogglePriceDisplayMode={toggleCurrencyMode}
                    onCotizarClick={setCotizarProperty}
                    onVerMasClick={setDetailProperty}
                    onWhatsAppClick={setWhatsAppProperty}
                    onSimulateMortgage={handleOpenMortgageSimulator}
                    isHovered={hoveredPropertyId === property.id}
                    onHover={setHoveredPropertyId}
                  />
                ))}
              </div>
            )}

            {/* View Mode: Split (List on Left + Interactive Map on Right) */}
            {viewMode === 'split' && (
              <div className={`grid gap-5 ${isMobile ? 'grid-cols-1' : 'grid-cols-1 xl:grid-cols-12'}`}>
                <div className={`${isMobile ? '' : 'xl:col-span-7'} space-y-4 ${
                  mobileActiveTab === 'mapa' ? (isMobile ? 'hidden' : 'hidden xl:block') : 'block'
                }`}>
                  <div className={`grid gap-4 ${isMobile ? 'grid-cols-1' : 'grid-cols-1 sm:grid-cols-2'}`}>
                    {filteredProperties.map((property) => (
                      <ListingCard
                        key={property.id}
                        property={property}
                        isFavorite={favorites.has(property.id)}
                        onToggleFavorite={handleToggleFavorite}
                        isCompared={comparedIds.has(property.id)}
                        onToggleCompare={handleToggleCompare}
                        canCompare={comparedIds.size < 3}
                        priceDisplayMode={priceDisplayMode}
                        onTogglePriceDisplayMode={toggleCurrencyMode}
                        onCotizarClick={setCotizarProperty}
                        onVerMasClick={setDetailProperty}
                        onWhatsAppClick={setWhatsAppProperty}
                        onSimulateMortgage={handleOpenMortgageSimulator}
                        isHovered={hoveredPropertyId === property.id}
                        onHover={setHoveredPropertyId}
                      />
                    ))}
                  </div>
                </div>

                <div className={`${isMobile ? 'h-[500px]' : 'xl:col-span-5 sticky top-36 h-[calc(100vh-160px)] min-h-[500px]'} ${
                  mobileActiveTab === 'lista' ? (isMobile ? 'hidden' : 'hidden xl:block') : 'block'
                }`}>
                  <InteractiveMap
                    properties={filteredProperties}
                    hoveredPropertyId={hoveredPropertyId}
                    onHoverProperty={setHoveredPropertyId}
                    selectedProperty={selectedMapProperty}
                    onSelectProperty={(prop) => {
                      setSelectedMapProperty(prop);
                      if (prop) setDetailProperty(prop);
                    }}
                    onContactProperty={setCotizarProperty}
                    priceDisplayMode={priceDisplayMode}
                  />
                </div>
              </div>
            )}

            {/* View Mode: List (Horizontal Cards) */}
            {viewMode === 'list' && (
              <div className="space-y-4">
                {filteredProperties.map((property) => (
                  <ListingCard
                    key={property.id}
                    property={property}
                    isFavorite={favorites.has(property.id)}
                    onToggleFavorite={handleToggleFavorite}
                    isCompared={comparedIds.has(property.id)}
                    onToggleCompare={handleToggleCompare}
                    canCompare={comparedIds.size < 3}
                    priceDisplayMode={priceDisplayMode}
                    onTogglePriceDisplayMode={toggleCurrencyMode}
                    onCotizarClick={setCotizarProperty}
                    onVerMasClick={setDetailProperty}
                    onWhatsAppClick={setWhatsAppProperty}
                    onSimulateMortgage={handleOpenMortgageSimulator}
                    isHovered={hoveredPropertyId === property.id}
                    onHover={setHoveredPropertyId}
                    compactListMode={true}
                  />
                ))}
              </div>
            )}

            {/* View Mode: Map (Full Map Canvas) */}
            {viewMode === 'map' && (
              <div className="h-[750px] w-full rounded-2xl overflow-hidden border border-slate-200">
                <InteractiveMap
                  properties={filteredProperties}
                  hoveredPropertyId={hoveredPropertyId}
                  onHoverProperty={setHoveredPropertyId}
                  selectedProperty={selectedMapProperty}
                  onSelectProperty={(prop) => {
                    setSelectedMapProperty(prop);
                    if (prop) setDetailProperty(prop);
                  }}
                  onContactProperty={setCotizarProperty}
                  priceDisplayMode={priceDisplayMode}
                />
              </div>
            )}

          </div>
        )}

        {/* 7. Below Results Section: Pagination, Editorial Text, Mortgage Banner, Footer */}
        <BelowResultsSection
          onSelectCommune={(comuna) => {
            handleFilterChange({ comuna });
            setSearchQuery(comuna);
            window.scrollTo({ top: 0, behavior: 'smooth' });
          }}
          onOpenMortgageSimulator={() => handleOpenMortgageSimulator(null)}
          onLoadMore={() => {}}
        />

      </main>

      {/* Floating Currency Toggle (Permite cambiar UF/CLP en cualquier momento del scroll) */}
      <FloatingCurrencyBar
        priceDisplayMode={priceDisplayMode}
        onTogglePriceDisplayMode={toggleCurrencyMode}
        isMobileSimulated={isMobile}
      />

      {/* Mobile Floating "Ver en Mapa / Ver en Lista" Button */}
      <div className={`${isMobile ? 'sticky bottom-5 flex justify-center w-full' : 'xl:hidden fixed bottom-6 left-1/2 -translate-x-1/2'} z-40 pointer-events-none`}>
        <button
          type="button"
          onClick={() => setMobileActiveTab(mobileActiveTab === 'lista' ? 'mapa' : 'lista')}
          className="pointer-events-auto flex items-center gap-2 px-5 py-3 rounded-full bg-[#0f172a] text-white font-bold text-xs shadow-2xl hover:bg-slate-800 active:scale-95 transition-all border border-white/20 cursor-pointer"
        >
          {mobileActiveTab === 'lista' ? (
            <>
              <MapIcon className="w-4 h-4 text-[#14b8a6]" />
              <span>Ver en Mapa ({filteredProperties.length})</span>
            </>
          ) : (
            <>
              <List className="w-4 h-4 text-[#14b8a6]" />
              <span>Ver en Lista</span>
            </>
          )}
        </button>
      </div>

      {/* Sticky Compare Drawer when items are checked */}
      <CompareBar
        comparedProperties={comparedPropertiesList}
        onOpenCompareModal={() => setIsCompareModalOpen(true)}
        onRemoveFromCompare={handleRemoveFromCompare}
        onClearCompare={handleClearCompare}
      />

      {/* Slide-Over Right Drawer "Filtros" */}
      <FilterBottomSheet
        isOpen={isFilterDrawerOpen}
        onClose={() => setIsFilterDrawerOpen(false)}
        filters={filters}
        onFilterChange={handleFilterChange}
        onResetFilters={handleResetFilters}
        onOpenSaveAlertModal={() => {
          setIsFilterDrawerOpen(false);
          setIsSaveSearchModalOpen(true);
        }}
        resultsCount={filteredProperties.length}
      />

      {/* Modals */}
      <CompareModal
        isOpen={isCompareModalOpen}
        onClose={() => setIsCompareModalOpen(false)}
        properties={comparedPropertiesList}
        onRemoveProperty={handleRemoveFromCompare}
        onContactProperty={setCotizarProperty}
        onWhatsAppProperty={setWhatsAppProperty}
      />

      <CotizarModal
        isOpen={Boolean(cotizarProperty)}
        onClose={() => setCotizarProperty(null)}
        property={cotizarProperty}
      />

      <PropertyDetailModal
        isOpen={Boolean(detailProperty)}
        onClose={() => setDetailProperty(null)}
        property={detailProperty}
        isFavorite={detailProperty ? favorites.has(detailProperty.id) : false}
        onToggleFavorite={handleToggleFavorite}
        onOpenCotizar={(prop) => {
          setDetailProperty(null);
          setCotizarProperty(prop);
        }}
        onOpenWhatsApp={(prop) => {
          setDetailProperty(null);
          setWhatsAppProperty(prop);
        }}
        onOpenMortgageSimulator={(prop) => {
          setDetailProperty(null);
          handleOpenMortgageSimulator(prop);
        }}
      />

      <MortgageSimulatorModal
        isOpen={isMortgageSimulatorOpen}
        onClose={() => setIsMortgageSimulatorOpen(false)}
        initialProperty={mortgageSimulatorProperty}
      />

      <WhatsAppModal
        isOpen={Boolean(whatsAppProperty)}
        onClose={() => setWhatsAppProperty(null)}
        property={whatsAppProperty}
      />

      <SaveSearchModal
        isOpen={isSaveSearchModalOpen}
        onClose={() => setIsSaveSearchModalOpen(false)}
        filters={filters}
        searchQuery={searchQuery}
      />

    </div>
  );

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col">
      {/* Top Simulation Toolbar */}
      <ViewportToggleBar
        viewportMode={viewportMode}
        onViewportModeChange={setViewportMode}
        appState={appState}
        onAppStateChange={handleAppStateChange}
        isCollapsed={isToolbarCollapsed}
        onToggleCollapse={() => setIsToolbarCollapsed((prev) => !prev)}
      />

      {/* Viewport Renderers */}
      {viewportMode === 'mobile' ? (
        <div className="flex-1 flex items-center justify-center py-6 px-2 bg-slate-950 overflow-x-auto">
          <div className="w-[390px] h-[844px] bg-slate-50 rounded-[48px] shadow-2xl border-[10px] border-slate-800 overflow-y-auto relative flex flex-col">
            <div className="sticky top-0 z-50 bg-slate-800 h-6 flex items-center justify-center">
              <div className="w-20 h-3.5 bg-black rounded-full" />
            </div>
            <div className="flex-1">
              {renderAppContent()}
            </div>
            <div className="sticky bottom-0 z-50 bg-white/80 backdrop-blur-xs py-1.5 flex justify-center pointer-events-none">
              <div className="w-32 h-1 bg-slate-800 rounded-full" />
            </div>
          </div>
        </div>
      ) : viewportMode === 'desktop' ? (
        <div className="flex-1 bg-slate-950 flex justify-center overflow-x-auto py-2">
          <div className="w-[1440px] bg-slate-50 shadow-2xl border border-slate-800 min-h-screen">
            {renderAppContent()}
          </div>
        </div>
      ) : (
        <div className="flex-1">
          {renderAppContent()}
        </div>
      )}
    </div>
  );
}
