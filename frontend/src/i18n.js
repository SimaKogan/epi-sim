// Palette shared by canvas drawing and CSS legends.
export const COLORS = {
  S: '#7aa2ff', // susceptible
  V: '#22d3ee', // vaccinated (not infected)
  E: '#fbbf24', // exposed
  I: '#ff4d6d', // infectious
  R: '#4ade80', // recovered
  D: '#6b7280', // dead
};

// Distinct hue per virus variant (index = variant id).
export const VARIANT_COLORS = [
  '#ff4d6d', '#ff9f43', '#f7d046', '#8be15a', '#2dd4bf', '#5ea0ff', '#a78bfa', '#f472b6',
];

const dict = {
  en: {
    subtitle: 'Agent-based SEIR model · Spring Boot + React',
    running: 'Running', paused: 'Paused', contained: 'Contained', connecting: 'Connecting…',
    day: 'Day', pause: 'Pause', resume: 'Resume', reset: 'New simulation',
    population: 'Population', initialCases: 'Initial cases', start: 'Start',
    cancel: 'Cancel',
    mapHint: 'Click the map to seed an outbreak',
    vaccination: 'Vaccination', vaccinationRate: 'Vaccination rate', perDay: '% of people / day',
    mutation: 'Mutation', mutationRate: 'Mutation pressure',
    restrictions: 'Restrictions', lockdown: 'Lockdown', masks: 'Masks', testing: 'Testing & isolation',
    speed: 'Speed', speedUnit: 'days / sec', simulation: 'Simulation',
    disease: 'Disease', infectiousness: 'Infectiousness', contactRadius: 'Contact radius',
    infectiousDays: 'Days infectious', days: 'days', fatality: 'Fatality rate', vaccineEfficacy: 'Vaccine efficacy',
    d_infectiousness: 'How easily the virus passes on at each close contact (×1 = baseline).',
    d_contactRadius: 'How close two people must be to count as a contact.',
    d_infectiousDays: 'How long a sick person stays contagious.',
    d_fatality: 'Share of infected people who die (higher for the elderly).',
    d_vaccineEfficacy: 'How much a vaccine lowers the chance of infection (less against escaping variants).',
    d_vaccination: 'Vaccinated people become much harder to infect and far less likely to die.',
    d_mutation: 'Higher pressure means new variants emerge more often — some spread faster or evade immunity.',
    d_lockdown: 'Share of people who stay at home: they neither catch nor spread the virus.',
    d_masks: 'Cuts the chance of transmission on every close contact.',
    d_testing: 'Infectious people are detected and isolated sooner.',
    presets: 'Scenarios',
    p_free: 'No measures', p_soft: 'Masks + testing', p_lock: 'Hard lockdown', p_vax: 'Vaccine race',
    sS: 'Susceptible', sV: 'Vaccinated', sE: 'Exposed', sI: 'Infectious', sR: 'Recovered', sD: 'Deceased',
    rt: 'Rt (estimate)', peak: 'Peak infectious', peakDay: 'on day {0}', everInfected: 'Infections per person', inclReinf: 'incl. reinfections',
    vaccinatedPct: 'Vaccinated', deaths: 'Deaths',
    chartTitle: 'Population over time', rtTitle: 'Reproduction number Rt', tooltipDay: 'Day {0}',
    variants: 'Virus variants', active: 'active', beta: 'infectiousness', escape: 'immune escape',
    lethality: 'lethality', born: 'day {0}',
    events: 'Events', noEvents: 'Nothing yet',
    'log.start': 'Simulation started: {0} people, {1} initial cases in {2}.',
    'log.variant': 'New variant {0} emerged in {1} (infectiousness ×{2}, immune escape {3}%, lethality ×{4}).',
    'log.death': 'First death recorded.',
    'log.milestone': '{0}% of the population has been infected at least once.',
    'log.contained': 'Outbreak contained: no active cases left.',
    'log.manual': 'Manual outbreak: {0} new {1} cases.',
    offline: 'Cannot reach the backend. Start it with: mvn spring-boot:run',
  },
  ru: {
    subtitle: 'Агентная SEIR-модель · Spring Boot + React',
    running: 'Идёт', paused: 'Пауза', contained: 'Подавлена', connecting: 'Подключение…',
    day: 'День', pause: 'Пауза', resume: 'Продолжить', reset: 'Новая симуляция',
    population: 'Население', initialCases: 'Начальных случаев', start: 'Запустить',
    cancel: 'Отмена',
    mapHint: 'Нажмите на карту, чтобы создать очаг',
    vaccination: 'Вакцинация', vaccinationRate: 'Темп вакцинации', perDay: '% людей / день',
    mutation: 'Мутации', mutationRate: 'Давление мутаций',
    restrictions: 'Ограничения', lockdown: 'Локдаун', masks: 'Маски', testing: 'Тесты и изоляция',
    speed: 'Скорость', speedUnit: 'дней / сек', simulation: 'Симуляция',
    disease: 'Болезнь', infectiousness: 'Заразность', contactRadius: 'Радиус контакта',
    infectiousDays: 'Дней заразности', days: 'дн.', fatality: 'Смертность', vaccineEfficacy: 'Эффективность вакцины',
    d_infectiousness: 'Насколько легко вирус передаётся при каждом близком контакте (×1 = базовая).',
    d_contactRadius: 'На каком расстоянии два человека считаются контактирующими.',
    d_infectiousDays: 'Сколько дней больной остаётся заразным.',
    d_fatality: 'Доля заразившихся, которые умирают (у пожилых выше).',
    d_vaccineEfficacy: 'Насколько вакцина снижает шанс заражения (слабее против вариантов с обходом иммунитета).',
    d_vaccination: 'Привитые заражаются заметно реже и гораздо реже умирают.',
    d_mutation: 'Чем выше, тем чаще появляются новые варианты: быстрее заражают или обходят иммунитет.',
    d_lockdown: 'Доля людей, сидящих дома: они не заражаются и не заражают.',
    d_masks: 'Снижают шанс заражения при каждом близком контакте.',
    d_testing: 'Заразных быстрее выявляют и изолируют.',
    presets: 'Сценарии',
    p_free: 'Без мер', p_soft: 'Маски + тесты', p_lock: 'Жёсткий локдаун', p_vax: 'Гонка вакцин',
    sS: 'Восприимчивые', sV: 'Привитые', sE: 'Инкубация', sI: 'Заразные', sR: 'Выздоровевшие', sD: 'Умершие',
    rt: 'Rt (оценка)', peak: 'Пик заразных', peakDay: 'на день {0}', everInfected: 'Заражений на человека', inclReinf: 'с учётом повторных',
    vaccinatedPct: 'Привито', deaths: 'Смертей',
    chartTitle: 'Население во времени', rtTitle: 'Репродуктивное число Rt', tooltipDay: 'День {0}',
    variants: 'Варианты вируса', active: 'активных', beta: 'заразность', escape: 'обход иммунитета',
    lethality: 'летальность', born: 'день {0}',
    events: 'События', noEvents: 'Пока ничего',
    'log.start': 'Старт: {0} человек, {1} начальных случаев, город {2}.',
    'log.variant': 'Новый вариант {0} появился в {1} (заразность ×{2}, обход иммунитета {3}%, летальность ×{4}).',
    'log.death': 'Зафиксирована первая смерть.',
    'log.milestone': '{0}% населения переболело хотя бы раз.',
    'log.contained': 'Вспышка подавлена: активных случаев нет.',
    'log.manual': 'Ручной очаг: {0} новых случаев ({1}).',
    offline: 'Нет связи с бэкендом. Запустите: mvn spring-boot:run',
  },
};

export function makeT(lang) {
  return (key, ...args) => {
    let s = (dict[lang] && dict[lang][key]) ?? dict.en[key] ?? key;
    args.forEach((a, i) => { s = s.split(`{${i}}`).join(String(a)); });
    return s;
  };
}

export function formatLog(t, entry) {
  const key = `log.${entry.type}`;
  const known = dict.en[key] !== undefined;
  return known ? t(key, ...(entry.args || [])) : entry.msg;
}
