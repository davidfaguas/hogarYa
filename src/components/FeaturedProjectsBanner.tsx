import React from 'react';
import { Property } from '../types/property';

interface FeaturedProjectsBannerProps {
  onOpenCotizar: (property: Property) => void;
  properties: Property[];
}

export const FeaturedProjectsBanner: React.FC<FeaturedProjectsBannerProps> = ({
  onOpenCotizar,
  properties
}) => {
  const featuredBanners = [
    {
      id: 'banner-1',
      title: 'PUCARÁ',
      subtitle: 'Vive cerca de todo en un entorno residencial',
      comuna: 'ÑUÑOA',
      tag: 'INSPIRA',
      image: 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80',
      propertyIndex: 0
    },
    {
      id: 'banner-2',
      title: 'BROWN NORTE',
      subtitle: 'Espacios pensados para tu familia y futuro',
      comuna: 'ÑUÑOA',
      tag: 'HOGARYA PREMIER',
      image: 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=800&q=80',
      propertyIndex: 1
    },
    {
      id: 'banner-3',
      title: 'PEDRO TORRES',
      subtitle: 'Diseño de vanguardia, amplios quinchos y terraza',
      comuna: 'ÑUÑOA',
      tag: 'INSPIRA',
      image: 'https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=800&q=80',
      propertyIndex: 2
    }
  ];

  return (
    <div className="mb-6">
      <div className="flex items-center justify-between mb-2.5">
        <div className="flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-[#0d9488]" />
          <h2 className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Proyectos Destacados en HogarYa
          </h2>
        </div>
        <span className="text-[11px] text-[#0d9488] font-semibold">
          Convenio Inmobiliario 2026
        </span>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-3.5">
        {featuredBanners.map((banner) => {
          const targetProperty = properties[banner.propertyIndex] || properties[0];
          return (
            <div
              key={banner.id}
              className="group relative h-40 sm:h-44 rounded-2xl overflow-hidden shadow-sm hover:shadow-md transition-all cursor-pointer border border-slate-200"
              onClick={() => targetProperty && onOpenCotizar(targetProperty)}
            >
              {/* Background photo */}
              <img
                src={banner.image}
                alt={banner.title}
                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
              />
              
              {/* Dark subtle gradient overlay */}
              <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/45 to-black/25" />

              {/* Tag in top right */}
              <div className="absolute top-3 right-3 px-2 py-0.5 bg-[#0f172a] text-white text-[10px] font-extrabold tracking-wider rounded uppercase flex items-center gap-1 shadow border border-white/10">
                <span className="w-1.5 h-1.5 rounded-full bg-[#0d9488]" />
                {banner.tag}
              </div>

              {/* Content in center / bottom */}
              <div className="absolute inset-x-4 bottom-3 flex items-end justify-between gap-2">
                <div>
                  <span className="text-[11px] font-bold tracking-widest text-[#14b8a6] uppercase block">
                    {banner.title}
                  </span>
                  <h4 className="text-sm sm:text-base font-extrabold text-white leading-tight">
                    {banner.subtitle}
                  </h4>
                  <div className="inline-block mt-0.5 px-2 py-0.5 bg-white/20 backdrop-blur-xs text-white text-[10px] font-bold tracking-wider rounded">
                    {banner.comuna}
                  </div>
                </div>

                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    if (targetProperty) onOpenCotizar(targetProperty);
                  }}
                  className="px-3.5 py-1.5 rounded-lg bg-white hover:bg-slate-100 text-slate-900 font-bold text-xs shadow-md transition-transform group-hover:scale-105 active:scale-95 shrink-0 cursor-pointer"
                >
                  Cotiza aquí
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
