// test bot for the shop menu
// usage: node bot.js trade|check|denied

const mineflayer = require('mineflayer')

const mode = process.argv[2] || 'check'
const bot = mineflayer.createBot({
  host: process.env.MC_HOST || '127.0.0.1',
  port: Number(process.env.MC_PORT || 25599),
  username: process.env.MC_USER || 'Tester',
  auth: 'offline',
  version: '1.21.11'
})

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const log = (...args) => console.log(`[bot ${new Date().toISOString().slice(11, 19)}]`, ...args)

bot.on('messagestr', (text, position) => {
  if (position !== 'game_info') log('chat:', text)
})
bot.on('kicked', (reason) => { log('kicked:', JSON.stringify(reason)); process.exit(2) })
bot.on('error', (err) => { log('error:', err.message); process.exit(2) })

function count (name) {
  return bot.inventory.items().filter((i) => i.name === name).reduce((sum, i) => sum + i.count, 0)
}

async function openShop (command) {
  const opened = new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('menu did not open')), 5000)
    bot.once('windowOpen', (window) => { clearTimeout(timer); resolve(window) })
  })
  bot.chat(command)
  const window = await opened
  log(`opened "${plain(window.title)}" with ${window.containerItems().length} items`)
  return window
}

// 1.21 sends text as nbt
function plain (node) {
  if (node == null) return ''
  if (typeof node === 'string') {
    if (!/^[[{]/.test(node)) return node
    try { return plain(JSON.parse(node)) } catch { return node }
  }
  if (typeof node !== 'object') return String(node)
  if (Array.isArray(node)) return node.map(plain).join('')
  if (node.type && 'value' in node) return plain(node.value)
  let text = ''
  if (node.text !== undefined) text += plain(node.text)
  if (node[''] !== undefined) text += plain(node[''])
  if (node.extra !== undefined) text += plain(node.extra)
  return text
}

function describe (window, slot) {
  const item = window.slots[slot]
  if (!item) return '(empty)'
  const lore = (item.customLore || []).map(plain).filter((l) => l).join(' | ')
  return `${item.name} x${item.count} [${plain(item.customName)}] ${lore}`
}

// mode 1 = shift click, button 1 = right
async function click (window, slot, button, shift) {
  await bot.clickWindow(slot, button, shift ? 1 : 0)
  await sleep(700)
}

async function trade () {
  log('balance check and coal before:', count('coal'))
  const window = await openShop('/shop ores')
  log('slot 0:', describe(window, 0))
  await click(window, 0, 0, false) // buy 1
  await click(window, 0, 0, true) // buy a stack (64)
  await click(window, 0, 1, false) // sell 1
  log('coal before sell all:', count('coal'))
  await click(window, 0, 1, true) // sell all
  log('slot 0 after trades:', describe(bot.currentWindow || window, 0))
  bot.closeWindow(bot.currentWindow || window)
  await sleep(500)
  log('coal after trades:', count('coal'))
}

async function check () {
  bot.chat('/shop price coal')
  await sleep(800)
  bot.chat('/shop history')
  await sleep(1500)
  log('coal in inventory:', count('coal'))
}

async function denied () {
  const window = await openShop('/shop ores')
  log('slot 0:', describe(window, 0))
  log('slot 49:', describe(window, 49))
  await click(window, 0, 0, false)
  bot.closeWindow(bot.currentWindow || window)
  log('coal in inventory:', count('coal'))
}

bot.once('spawn', async () => {
  log('spawned as', bot.username)
  await sleep(2000)
  try {
    if (mode === 'trade') await trade()
    else if (mode === 'denied') await denied()
    else await check()
  } catch (err) {
    log('FAILED:', err.message)
    process.exitCode = 1
  }
  await sleep(500)
  bot.quit()
  setTimeout(() => process.exit(), 1000)
})
