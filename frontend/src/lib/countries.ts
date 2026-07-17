/**
 * ISO-3166 alpha-2 ülke listesi — uyruk seçimi ve telefon ülke kodu tek kaynaktan beslenir
 * (DB: passengers.nationality varchar(2), TourVisio Nationality + ContactPhone.countryCode).
 *
 * `dial` E.164 ÜLKE kodudur ('+' olmadan), alan kodu DEĞİL: NANP ülkelerinin (ABD, Kanada,
 * Karayipler) hepsi '1' taşır — 242/1242 gibi karışımlar yerine alan kodu ulusal numaranın
 * (NSN) parçasıdır. `nsn` yalnızca emin olduğumuz ülkelerde doludur; boşsa NSN_FALLBACK
 * uygulanır (E.164 toplam 15 hane sınırı).
 */

export interface Country {
  /** ISO-3166 alpha-2, büyük harf. */
  code: string
  /** Türkçe ülke adı. */
  name: string
  /** E.164 ülke arama kodu, '+' olmadan (ör. Türkiye → '90'). */
  dial: string
  /** Ulusal numaranın (NSN) izinli hane aralığı [min, max]. */
  nsn?: [number, number]
  /**
   * Uluslararası biçimde baştaki 0'ı KORUYAN ülke. Neredeyse her ülkede baştaki 0 bir şehirlerarası
   * (trunk) önekidir ve ülke kodu ile birlikte düşer; İtalya sabit hatlarında ise numaranın parçasıdır.
   */
  keepsTrunkZero?: true
  /**
   * Ulusal numaranın başlayabileceği haneler — yalnız emin olduğumuz ülkelerde doludur.
   * Hane SAYISI tek başına yetmiyor: kullanıcı ülke kodunu numaranın içine de yazarsa
   * (TR'de '90' + kırpılmış numara) sonuç doğru uzunlukta ama yanlış bir numara olabiliyor.
   */
  nsnStart?: { re: RegExp; hint: string }
}

/** NSN'i bilinmeyen ülkeler için makul aralık (E.164: ülke kodu dahil en çok 15 hane). */
export const NSN_FALLBACK: [number, number] = [5, 14]

/** Varsayılan ülke — uygulama Türkiye pazarına konuşuyor. */
export const DEFAULT_COUNTRY_CODE = 'TR'

/** NANP: tek ülke kodu (+1), alan kodu ulusal numaranın içinde → NSN her zaman 10. */
const NANP: [number, number] = [10, 10]

type Row = [code: string, name: string, dial: string, nsn?: [number, number]]

const ROWS: Row[] = [
  ['AF', 'Afganistan', '93'],
  ['DE', 'Almanya', '49', [6, 11]],
  ['US', 'Amerika Birleşik Devletleri', '1', NANP],
  ['AD', 'Andorra', '376', [6, 6]],
  ['AO', 'Angola', '244', [9, 9]],
  ['AI', 'Anguilla', '1', NANP],
  ['AG', 'Antigua ve Barbuda', '1', NANP],
  ['AR', 'Arjantin', '54'],
  ['AL', 'Arnavutluk', '355', [9, 9]],
  ['AW', 'Aruba', '297', [7, 7]],
  ['AU', 'Avustralya', '61', [9, 9]],
  ['AT', 'Avusturya', '43'],
  ['AZ', 'Azerbaycan', '994', [9, 9]],
  ['BS', 'Bahamalar', '1', NANP],
  ['BH', 'Bahreyn', '973', [8, 8]],
  ['BD', 'Bangladeş', '880', [10, 10]],
  ['BB', 'Barbados', '1', NANP],
  ['BY', 'Belarus', '375', [9, 9]],
  ['BE', 'Belçika', '32', [8, 9]],
  ['BZ', 'Belize', '501', [7, 7]],
  ['BJ', 'Benin', '229', [8, 8]],
  ['BM', 'Bermuda', '1', NANP],
  ['AE', 'Birleşik Arap Emirlikleri', '971', [8, 9]],
  ['GB', 'Birleşik Krallık', '44', [9, 10]],
  ['BO', 'Bolivya', '591', [8, 8]],
  ['BA', 'Bosna-Hersek', '387', [8, 8]],
  ['BW', 'Botsvana', '267', [7, 8]],
  ['BR', 'Brezilya', '55', [10, 11]],
  ['BN', 'Brunei', '673', [7, 7]],
  ['BG', 'Bulgaristan', '359', [8, 9]],
  ['BF', 'Burkina Faso', '226', [8, 8]],
  ['BI', 'Burundi', '257', [8, 8]],
  ['BT', 'Butan', '975', [8, 8]],
  ['CV', 'Cabo Verde', '238', [7, 7]],
  ['GI', 'Cebelitarık', '350', [8, 8]],
  ['DZ', 'Cezayir', '213', [8, 9]],
  ['DJ', 'Cibuti', '253', [8, 8]],
  ['TD', 'Çad', '235', [8, 8]],
  ['CZ', 'Çekya', '420', [9, 9]],
  ['CN', 'Çin', '86', [11, 11]],
  ['DK', 'Danimarka', '45', [8, 8]],
  ['DM', 'Dominika', '1', NANP],
  ['DO', 'Dominik Cumhuriyeti', '1', NANP],
  ['EC', 'Ekvador', '593', [8, 9]],
  ['GQ', 'Ekvator Ginesi', '240', [9, 9]],
  ['SV', 'El Salvador', '503', [8, 8]],
  ['ID', 'Endonezya', '62', [9, 12]],
  ['ER', 'Eritre', '291', [7, 7]],
  ['AM', 'Ermenistan', '374', [8, 8]],
  ['EE', 'Estonya', '372', [7, 8]],
  ['SZ', 'Esvatini', '268', [8, 8]],
  ['ET', 'Etiyopya', '251', [9, 9]],
  ['MA', 'Fas', '212', [9, 9]],
  ['FO', 'Faroe Adaları', '298', [6, 6]],
  ['FJ', 'Fiji', '679', [7, 7]],
  ['CI', 'Fildişi Sahili', '225', [10, 10]],
  ['PH', 'Filipinler', '63', [10, 10]],
  ['PS', 'Filistin', '970', [9, 9]],
  ['FI', 'Finlandiya', '358'],
  ['FR', 'Fransa', '33', [9, 9]],
  ['PF', 'Fransız Polinezyası', '689', [8, 8]],
  ['GA', 'Gabon', '241', [7, 8]],
  ['GM', 'Gambiya', '220', [7, 7]],
  ['GH', 'Gana', '233', [9, 9]],
  ['GN', 'Gine', '224', [9, 9]],
  ['GW', 'Gine-Bissau', '245', [7, 7]],
  ['GD', 'Grenada', '1', NANP],
  ['GL', 'Grönland', '299', [6, 6]],
  ['GP', 'Guadeloupe', '590', [9, 9]],
  ['GU', 'Guam', '1', NANP],
  ['GT', 'Guatemala', '502', [8, 8]],
  ['GY', 'Guyana', '592', [7, 7]],
  ['ZA', 'Güney Afrika', '27', [9, 9]],
  ['KR', 'Güney Kore', '82', [8, 10]],
  ['SS', 'Güney Sudan', '211', [9, 9]],
  ['GE', 'Gürcistan', '995', [9, 9]],
  ['HT', 'Haiti', '509', [8, 8]],
  ['IN', 'Hindistan', '91', [10, 10]],
  ['HR', 'Hırvatistan', '385', [8, 9]],
  ['NL', 'Hollanda', '31', [9, 9]],
  ['HN', 'Honduras', '504', [8, 8]],
  ['HK', 'Hong Kong', '852', [8, 8]],
  ['IQ', 'Irak', '964', [10, 10]],
  ['IR', 'İran', '98', [10, 10]],
  ['IE', 'İrlanda', '353', [7, 9]],
  ['ES', 'İspanya', '34', [9, 9]],
  ['IL', 'İsrail', '972', [8, 9]],
  ['SE', 'İsveç', '46', [7, 9]],
  ['CH', 'İsviçre', '41', [9, 9]],
  ['IT', 'İtalya', '39', [6, 11]],
  ['IS', 'İzlanda', '354', [7, 7]],
  ['JM', 'Jamaika', '1', NANP],
  ['JP', 'Japonya', '81', [9, 10]],
  ['KH', 'Kamboçya', '855', [8, 9]],
  ['CM', 'Kamerun', '237', [9, 9]],
  ['CA', 'Kanada', '1', NANP],
  ['ME', 'Karadağ', '382', [8, 8]],
  ['QA', 'Katar', '974', [8, 8]],
  ['KZ', 'Kazakistan', '7', [10, 10]],
  ['KE', 'Kenya', '254', [9, 9]],
  ['CY', 'Kıbrıs', '357', [8, 8]],
  ['KG', 'Kırgızistan', '996', [9, 9]],
  ['KI', 'Kiribati', '686', [5, 8]],
  ['CO', 'Kolombiya', '57', [10, 10]],
  ['KM', 'Komorlar', '269', [7, 7]],
  ['CG', 'Kongo', '242', [9, 9]],
  ['CD', 'Kongo Demokratik Cumhuriyeti', '243', [9, 9]],
  ['XK', 'Kosova', '383', [8, 9]],
  ['CR', 'Kosta Rika', '506', [8, 8]],
  ['KW', 'Kuveyt', '965', [8, 8]],
  ['KP', 'Kuzey Kore', '850'],
  ['MK', 'Kuzey Makedonya', '389', [8, 8]],
  ['CU', 'Küba', '53', [8, 8]],
  ['KY', 'Cayman Adaları', '1', NANP],
  ['LA', 'Laos', '856', [8, 10]],
  ['LS', 'Lesotho', '266', [8, 8]],
  ['LV', 'Letonya', '371', [8, 8]],
  ['LR', 'Liberya', '231', [7, 9]],
  ['LY', 'Libya', '218', [9, 9]],
  ['LI', 'Liechtenstein', '423', [7, 7]],
  ['LT', 'Litvanya', '370', [8, 8]],
  ['LB', 'Lübnan', '961', [7, 8]],
  ['LU', 'Lüksemburg', '352', [9, 9]],
  ['HU', 'Macaristan', '36', [8, 9]],
  ['MG', 'Madagaskar', '261', [9, 9]],
  ['MO', 'Makao', '853', [8, 8]],
  ['MW', 'Malavi', '265', [7, 9]],
  ['MV', 'Maldivler', '960', [7, 7]],
  ['MY', 'Malezya', '60', [7, 10]],
  ['ML', 'Mali', '223', [8, 8]],
  ['MT', 'Malta', '356', [8, 8]],
  ['MH', 'Marshall Adaları', '692', [7, 7]],
  ['MQ', 'Martinik', '596', [9, 9]],
  ['MX', 'Meksika', '52', [10, 10]],
  ['MU', 'Mauritius', '230', [7, 8]],
  ['EG', 'Mısır', '20', [9, 10]],
  ['MN', 'Moğolistan', '976', [8, 8]],
  ['MD', 'Moldova', '373', [8, 8]],
  ['MC', 'Monako', '377', [8, 9]],
  ['MS', 'Montserrat', '1', NANP],
  ['MR', 'Moritanya', '222', [8, 8]],
  ['MZ', 'Mozambik', '258', [9, 9]],
  ['MM', 'Myanmar', '95', [8, 10]],
  ['NA', 'Namibya', '264', [9, 9]],
  ['NR', 'Nauru', '674', [7, 7]],
  ['NP', 'Nepal', '977', [10, 10]],
  ['NE', 'Nijer', '227', [8, 8]],
  ['NG', 'Nijerya', '234', [10, 10]],
  ['NI', 'Nikaragua', '505', [8, 8]],
  ['NO', 'Norveç', '47', [8, 8]],
  ['CF', 'Orta Afrika Cumhuriyeti', '236', [8, 8]],
  ['UZ', 'Özbekistan', '998', [9, 9]],
  ['PK', 'Pakistan', '92', [10, 10]],
  ['PW', 'Palau', '680', [7, 7]],
  ['PA', 'Panama', '507', [7, 8]],
  ['PG', 'Papua Yeni Gine', '675', [8, 8]],
  ['PY', 'Paraguay', '595', [9, 9]],
  ['PE', 'Peru', '51', [8, 9]],
  ['PL', 'Polonya', '48', [9, 9]],
  ['PT', 'Portekiz', '351', [9, 9]],
  ['PR', 'Porto Riko', '1', NANP],
  ['RE', 'Réunion', '262', [9, 9]],
  ['RO', 'Romanya', '40', [9, 9]],
  ['RW', 'Ruanda', '250', [9, 9]],
  ['RU', 'Rusya', '7', [10, 10]],
  ['WS', 'Samoa', '685', [5, 7]],
  ['SM', 'San Marino', '378', [8, 10]],
  ['ST', 'São Tomé ve Príncipe', '239', [7, 7]],
  ['SN', 'Senegal', '221', [9, 9]],
  ['SC', 'Seyşeller', '248', [7, 7]],
  ['RS', 'Sırbistan', '381', [8, 9]],
  ['SL', 'Sierra Leone', '232', [8, 8]],
  ['SG', 'Singapur', '65', [8, 8]],
  ['SX', 'Sint Maarten', '1', NANP],
  ['SK', 'Slovakya', '421', [9, 9]],
  ['SI', 'Slovenya', '386', [8, 8]],
  ['SB', 'Solomon Adaları', '677', [5, 7]],
  ['SO', 'Somali', '252', [7, 9]],
  ['LK', 'Sri Lanka', '94', [9, 9]],
  ['SD', 'Sudan', '249', [9, 9]],
  ['SR', 'Surinam', '597', [6, 7]],
  ['SY', 'Suriye', '963', [9, 9]],
  ['SA', 'Suudi Arabistan', '966', [8, 9]],
  ['KN', 'Saint Kitts ve Nevis', '1', NANP],
  ['LC', 'Saint Lucia', '1', NANP],
  ['VC', 'Saint Vincent ve Grenadinler', '1', NANP],
  ['TJ', 'Tacikistan', '992', [9, 9]],
  ['TZ', 'Tanzanya', '255', [9, 9]],
  ['TH', 'Tayland', '66', [9, 9]],
  ['TW', 'Tayvan', '886', [8, 9]],
  ['TG', 'Togo', '228', [8, 8]],
  ['TO', 'Tonga', '676', [5, 7]],
  ['TT', 'Trinidad ve Tobago', '1', NANP],
  ['TN', 'Tunus', '216', [8, 8]],
  ['TC', 'Turks ve Caicos Adaları', '1', NANP],
  ['TR', 'Türkiye', '90', [10, 10]],
  ['TM', 'Türkmenistan', '993', [8, 8]],
  ['TV', 'Tuvalu', '688', [5, 6]],
  ['UG', 'Uganda', '256', [9, 9]],
  ['UA', 'Ukrayna', '380', [9, 9]],
  ['OM', 'Umman', '968', [8, 8]],
  ['UY', 'Uruguay', '598', [8, 8]],
  ['JO', 'Ürdün', '962', [8, 9]],
  ['VU', 'Vanuatu', '678', [5, 7]],
  ['VA', 'Vatikan', '379'],
  ['VE', 'Venezuela', '58', [10, 10]],
  ['VN', 'Vietnam', '84', [9, 10]],
  ['VG', 'Britanya Virjin Adaları', '1', NANP],
  ['VI', 'ABD Virjin Adaları', '1', NANP],
  ['YE', 'Yemen', '967', [7, 9]],
  ['NC', 'Yeni Kaledonya', '687', [6, 6]],
  ['NZ', 'Yeni Zelanda', '64', [8, 10]],
  ['GR', 'Yunanistan', '30', [10, 10]],
  ['ZM', 'Zambiya', '260', [9, 9]],
  ['ZW', 'Zimbabve', '263', [9, 9]],
]

/** Türkçe alfabetik sıralı ülke listesi (İ/ı/Ş/Ç doğru sıralansın diye 'tr' collation). */
export const COUNTRIES: readonly Country[] = ROWS.map(([code, name, dial, nsn]) => {
  const country: Country = { code, name, dial }
  if (nsn) country.nsn = nsn
  // İtalya sabit hatlarında baştaki 0 numaranın parçasıdır, trunk öneki değil.
  if (code === 'IT') country.keepsTrunkZero = true
  // Türkiye alan/operatör kodları 2xx–5xx: numara 0, 1 ya da 9 ile BAŞLAMAZ. Bu kural
  // '90'ı numaranın içine yazan kullanıcıyı yakalar — '9055511122' 10 hanedir, uzunluk
  // kontrolünden geçer ve sessizce yanlış numara olarak giderdi.
  if (code === 'TR') {
    country.nsnStart = { re: /^[2-5]/, hint: '2 ile 5 arasında bir hane' }
  }
  return country
}).sort((a, b) => a.name.localeCompare(b.name, 'tr'))

const BY_CODE = new Map(COUNTRIES.map((c) => [c.code, c]))

/** ISO alpha-2 kodundan ülkeyi bulur (büyük/küçük harf duyarsız). Bilinmiyorsa undefined. */
export function findCountry(code: string | null | undefined): Country | undefined {
  return code ? BY_CODE.get(code.trim().toUpperCase()) : undefined
}

/** Kod gerçek bir ülkeye mi karşılık geliyor? ('ZZ' gibi iki harfli uydurmaları eler.) */
export function isCountryCode(code: string | null | undefined): boolean {
  return !!findCountry(code)
}

/** Ülkenin arama kodu, '+' ile (bilinmeyen kodda boş dize). */
export function dialCodeOf(code: string | null | undefined): string {
  const country = findCountry(code)
  return country ? `+${country.dial}` : ''
}

/** Ülkenin ulusal numara (NSN) hane aralığı; bilinmiyorsa NSN_FALLBACK. */
export function nsnRange(code: string | null | undefined): [number, number] {
  return findCountry(code)?.nsn ?? NSN_FALLBACK
}

/**
 * Kullanıcının yazdığını ulusal numaraya indirger: rakam dışını atar, baştaki trunk 0'ını düşürür
 * (İtalya hariç — orada 0 numaranın parçası). Ülke kodu ayrı bir alandan geldiği için numarada
 * ülke kodu taşınmaz.
 */
export function normalizeNationalNumber(raw: string, countryCode: string): string {
  const digits = (raw ?? '').replace(/\D/g, '')
  return findCountry(countryCode)?.keepsTrunkZero ? digits : digits.replace(/^0+/, '')
}

/**
 * Yazılan/yapıştırılan metni alana yazılacak ulusal numaraya çevirir: normalize eder, kullanıcı
 * ülke kodunu da yapıştırdıysa ('+90 555…') onu düşürür ve ülkenin en fazla hane sayısına kırpar.
 *
 * Ülke kodu YALNIZCA kalan kısım o ülkenin NSN aralığına oturuyorsa atılır; aksi hâlde numaranın
 * kendisi arama koduyla başlıyor olabilir (Rusya '+7' / '7123456789') ve baştaki haneyi yemek
 * numarayı bozardı.
 */
export function parseNationalNumber(raw: string, countryCode: string): string {
  const country = findCountry(countryCode)
  let digits = normalizeNationalNumber(raw, countryCode)
  const [min, max] = nsnRange(countryCode)
  if (country && digits.length > max && digits.startsWith(country.dial)) {
    const withoutDial = digits.slice(country.dial.length)
    if (withoutDial.length >= min && withoutDial.length <= max) digits = withoutDial
  }
  return digits.slice(0, max)
}
