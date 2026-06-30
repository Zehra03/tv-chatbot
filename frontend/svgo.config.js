// SVGO yapılandırması.
// Altın kural: viewBox'ı ASLA silme (ölçeklenebilirlik ona bağlı);
// bunun yerine sabit width/height'ı kaldır => responsive SVG.
export default {
  multipass: true,
  plugins: [
    {
      name: 'preset-default',
      params: {
        overrides: {
          removeViewBox: false, // <-- viewBox'ı koru
        },
      },
    },
    'removeDimensions', // width/height kaldır, viewBox kalsın
  ],
}
