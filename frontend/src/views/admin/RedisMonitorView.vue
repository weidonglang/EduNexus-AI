<script setup lang="ts">
// Redis 状态监控页面。
// 管理员可以在这里查看 Redis 连接状态、selection:* 缓存 Key、TTL、库存剩余量，
// 并按三位同学的分工展示库存扣减、请求幂等和短锁运行情况。
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import {
  prewarmRedisStockApi,
  redisMonitorApi,
  type RedisKeyRow,
  type RedisMonitorResponse,
} from '@/api/systemMonitor'

const loading = ref(false)
const prewarming = ref(false)
const pattern = ref('selection:*')
const limit = ref(200)
const data = ref<RedisMonitorResponse | null>(null)
const apiError = ref('')
const stockPage = ref(1)
const stockSize = ref(10)
const keyPage = ref(1)
const keySize = ref(10)

// 魏语石负责的库存 key：selection:offering:{offeringId}:remaining。
// 前端用这个正则把 Redis key 列表中的教学班库存缓存筛选出来。
const stockKeys = computed(() => (data.value?.keys ?? []).filter((item) => /^selection:offering:\d+:remaining$/.test(item.key)))
const requestKeys = computed(() => (data.value?.keys ?? []).filter((item) => item.key.startsWith('selection:request:')))
const lockKeys = computed(() => (data.value?.keys ?? []).filter((item) => item.key.startsWith('selection:grab:lock:')))
const sampleRequest = computed(() => requestKeys.value[0])
const sampleLock = computed(() => lockKeys.value[0])
const pagedStockChecks = computed(() => (data.value?.stockChecks ?? []).slice((stockPage.value - 1) * stockSize.value, stockPage.value * stockSize.value))
const pagedKeys = computed(() => (data.value?.keys ?? []).slice((keyPage.value - 1) * keySize.value, keyPage.value * keySize.value))

const ttlSummary = computed(() => {
  const keys = data.value?.keys ?? []
  return {
    expiring: keys.filter((item) => item.ttlSeconds > 0).length,
    persistent: keys.filter((item) => item.ttlSeconds === -1).length,
  }
})

onMounted(loadData)

async function loadData() {
  loading.value = true
  apiError.value = ''
  stockPage.value = 1
  keyPage.value = 1
  try {
    data.value = (await redisMonitorApi({ pattern: pattern.value.trim() || 'selection:*', limit: limit.value })).data
    if (!data.value.reachable) {
      ElMessage.warning('Redis 当前不可用，后端会自动使用数据库兜底')
    }
  } catch {
    data.value = null
    apiError.value = '后端接口连接失败，请确认 Spring Boot 本地 http://localhost:8080 或 Docker http://localhost:8088 已启动。'
  } finally {
    loading.value = false
  }
}

function handleStockSizeChange() {
  stockPage.value = 1
}

function handleKeySizeChange() {
  keyPage.value = 1
}

async function prewarmStock() {
  prewarming.value = true
  try {
    const result = (await prewarmRedisStockApi(20)).data
    ElMessage.success(`已预热 ${result.count} 个教学班库存 Key`)
    await loadData()
  } catch {
    ElMessage.error('预热 Redis 库存失败，请确认 Redis 和后端都已启动')
  } finally {
    prewarming.value = false
  }
}

function ttlText(ttl: number) {
  if (ttl > 0) return `${ttl} 秒`
  if (ttl === -1) return '长期有效'
  return '已过期或不存在'
}

function redisValueText(value?: number | null) {
  return value === null || value === undefined ? '未预热' : String(value)
}

function requestId(row?: RedisKeyRow) {
  return row?.key.replace('selection:request:', '') || '暂无正在展示的请求'
}

function lockOwner(row?: RedisKeyRow) {
  if (!row) return { offeringId: '暂无', username: '暂无' }
  const parts = row.key.split(':')
  return { offeringId: parts[3] || '未知', username: parts.slice(4).join(':') || '未知' }
}
</script>

<template>
  <PageHeader title="Redis 状态监控" description="按团队分工展示抢课库存、请求幂等、短锁和 Redis 运行状态。" />

  <section class="admin-toolbar">
    <div class="admin-summary">
      <article>
        <span>连接状态</span>
        <strong>{{ data?.reachable ? '正常' : '不可用' }}</strong>
      </article>
      <article>
        <span>Redis Key 总数</span>
        <strong>{{ data?.dbSize ?? 0 }}</strong>
      </article>
      <article>
        <span>本次查询耗时</span>
        <strong>{{ data?.elapsedMs ?? 0 }} ms</strong>
      </article>
    </div>
    <div class="admin-actions">
      <el-input v-model="pattern" class="keyword-input" placeholder="Redis Key 模式，例如 selection:*" clearable />
      <el-input-number v-model="limit" :min="1" :max="500" :step="50" />
      <el-button type="primary" :loading="loading" @click="loadData">刷新</el-button>
      <el-button type="success" :loading="prewarming" @click="prewarmStock">预热库存</el-button>
    </div>
  </section>

  <section v-if="data && !data.reachable" class="redis-warning">
    {{ data.error || 'Redis 连接失败' }}
  </section>
  <section v-if="apiError" class="redis-warning">{{ apiError }}</section>

  <section class="guide-panel">
    <div class="panel-heading">
      <h2>新手说明</h2>
      <span>先看懂这些词，再看下面的数据</span>
    </div>
    <div class="guide-grid">
      <article>
        <strong>Redis</strong>
        <p>放在内存里的高速缓存。抢课时先访问 Redis，可以减少数据库压力。</p>
      </article>
      <article>
        <strong>Key</strong>
        <p>缓存数据的名字，例如 selection:offering:1:remaining 表示 1 号教学班剩余名额。</p>
      </article>
      <article>
        <strong>TTL</strong>
        <p>缓存剩余过期时间。库存 Key 默认保留一段时间，过期后会自动删除。</p>
      </article>
      <article>
        <strong>库存 Key</strong>
        <p>selection:offering:{教学班ID}:remaining，用来保存某个教学班还剩多少名额。</p>
      </article>
      <article>
        <strong>幂等 Key</strong>
        <p>selection:request:{请求ID}，用来识别同一次抢课请求，防止重复提交。</p>
      </article>
      <article>
        <strong>短锁 Key</strong>
        <p>selection:grab:lock:{教学班ID}:{账号}，限制同一学生短时间重复抢同一门课。</p>
      </article>
    </div>
  </section>

  <section class="redis-owner-grid">
    <article class="work-panel owner-card stock-card">
      <div class="owner-heading">
        <span>魏语石</span>
        <h2>Redis 库存扣减检测</h2>
      </div>
      <p>负责抢课库存缓存、并发扣减、防超卖，以及数据库写入失败后的 Redis 库存回滚。没有 Redis Key 时也会显示数据库库存状态，点击“预热库存”后即可生成真实 Redis 库存 Key。</p>
      <el-table :data="pagedStockChecks" size="small" empty-text="暂无教学班库存数据">
        <el-table-column prop="offeringId" label="教学班 ID" width="96" />
        <el-table-column prop="key" label="Redis 库存 Key" min-width="260" show-overflow-tooltip />
        <el-table-column label="Redis 剩余" width="110">
          <template #default="{ row }">
            <el-tag :type="row.redisRemaining === null || row.redisRemaining === undefined ? 'info' : 'success'">
              {{ redisValueText(row.redisRemaining) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="databaseCapacity" label="数据库容量" width="100" />
        <el-table-column prop="databaseSelected" label="已选人数" width="96" />
        <el-table-column prop="databaseRemaining" label="数据库剩余" width="110" />
        <el-table-column label="是否超卖" width="96">
          <template #default="{ row }">
            <el-tag :type="row.oversold ? 'danger' : 'success'">{{ row.oversold ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="stockPage"
        v-model:page-size="stockSize"
        class="table-pagination compact-pagination"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50, 100]"
        :total="data?.stockChecks.length ?? 0"
        @size-change="handleStockSizeChange"
      />
    </article>

    <article class="work-panel owner-card">
      <div class="owner-heading">
        <span>郭凤圣</span>
        <h2>Redis 请求幂等检测</h2>
      </div>
      <p>负责 requestId 机制，使用 selection:request:{requestId} 拦截同一次抢课请求的重复提交。</p>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="请求 ID">{{ requestId(sampleRequest) }}</el-descriptions-item>
        <el-descriptions-item label="Redis 幂等 Key">{{ sampleRequest?.key || '暂无 selection:request:* Key' }}</el-descriptions-item>
        <el-descriptions-item label="Key 状态">{{ sampleRequest ? '已存在' : '暂无' }}</el-descriptions-item>
        <el-descriptions-item label="TTL">{{ sampleRequest ? ttlText(sampleRequest.ttlSeconds) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="重复提交处理">{{ sampleRequest ? '已拦截或复用结果' : '等待抢课请求生成' }}</el-descriptions-item>
      </el-descriptions>
    </article>

    <article class="work-panel owner-card">
      <div class="owner-heading">
        <span>敖东磊</span>
        <h2>Redis 短锁与运行状态检测</h2>
      </div>
      <p>负责 selection:grab:lock:{offeringId}:{username} 短锁、Redis PING 检测、缓存清理和降级说明。</p>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="Redis 连接状态">{{ data?.reachable ? '正常' : '不可用' }}</el-descriptions-item>
        <el-descriptions-item label="PING 结果">{{ data?.ping || '-' }}</el-descriptions-item>
        <el-descriptions-item label="短锁 Key">{{ sampleLock?.key || '暂无 selection:grab:lock:* Key' }}</el-descriptions-item>
        <el-descriptions-item label="教学班 / 学生">{{ lockOwner(sampleLock).offeringId }} / {{ lockOwner(sampleLock).username }}</el-descriptions-item>
        <el-descriptions-item label="TTL">{{ sampleLock ? ttlText(sampleLock.ttlSeconds) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="Redis 降级状态">{{ data?.reachable ? '未触发' : '已触发数据库兜底' }}</el-descriptions-item>
      </el-descriptions>
    </article>
  </section>

  <section class="redis-grid">
    <article class="work-panel">
      <div class="panel-heading">
        <h2>缓存分类概览</h2>
        <span>当前查询模式 {{ data?.pattern || pattern }}</span>
      </div>
      <div class="redis-overview">
        <div><span>库存 Key</span><strong>{{ stockKeys.length }}</strong></div>
        <div><span>幂等 Key</span><strong>{{ requestKeys.length }}</strong></div>
        <div><span>短锁 Key</span><strong>{{ lockKeys.length }}</strong></div>
        <div><span>带 TTL</span><strong>{{ ttlSummary.expiring }}</strong></div>
      </div>
      <p class="redis-note">库存 Key 可以通过“预热库存”立即生成；幂等 Key 和短锁 Key 的 TTL 较短，压测进行中或刚点击抢课后刷新更容易看到。</p>
    </article>

    <article class="work-panel">
      <div class="panel-heading">
        <h2>答辩讲解口径</h2>
        <span>真实缓存数据</span>
      </div>
      <ul class="talking-points">
        <li>魏语石：Redis 先扣库存，降低数据库压力，并验证数据库已选人数不超过容量。</li>
        <li>郭凤圣：每次抢课携带 requestId，Redis 幂等 Key 防止重复提交。</li>
        <li>敖东磊：短锁限制同一学生短时间重复抢同一教学班，并展示 PING 与降级状态。</li>
      </ul>
    </article>
  </section>

  <section v-loading="loading" class="work-panel">
    <div class="panel-heading">
      <h2>Redis 缓存键明细</h2>
      <span>{{ data?.keys.length ?? 0 }} 条</span>
    </div>
    <el-table :data="pagedKeys" empty-text="暂无 Redis 缓存键">
      <el-table-column prop="key" label="Key" min-width="300" show-overflow-tooltip />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <el-tag v-if="/^selection:offering:\d+:remaining$/.test(row.key)" type="success">库存</el-tag>
          <el-tag v-else-if="row.key.startsWith('selection:request:')" type="warning">幂等</el-tag>
          <el-tag v-else-if="row.key.startsWith('selection:grab:lock:')" type="info">短锁</el-tag>
          <el-tag v-else>其他</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="TTL" width="140">
        <template #default="{ row }">{{ ttlText(row.ttlSeconds) }}</template>
      </el-table-column>
      <el-table-column prop="value" label="Value" min-width="180" show-overflow-tooltip />
    </el-table>
    <el-pagination
      v-model:current-page="keyPage"
      v-model:page-size="keySize"
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="[10, 20, 50, 100]"
      :total="data?.keys.length ?? 0"
      @size-change="handleKeySizeChange"
    />
  </section>
</template>

<style scoped>
.guide-panel,
.work-panel {
  margin-bottom: 18px;
}

.guide-panel {
  padding: 18px;
  background: #ffffff;
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow: 0 10px 26px rgba(28, 45, 65, 0.05);
}

.guide-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.guide-grid article {
  padding: 13px;
  background: var(--panel-soft);
  border: 1px solid var(--line);
  border-radius: 8px;
}

.guide-grid strong {
  display: block;
  margin-bottom: 6px;
  color: #0f5f71;
}

.guide-grid p {
  margin: 0;
  color: var(--muted);
  line-height: 1.7;
}

.redis-owner-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(280px, 0.8fr) minmax(280px, 0.8fr);
  gap: 18px;
  margin-bottom: 18px;
}

.owner-card {
  min-width: 0;
}

.owner-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.owner-heading h2 {
  margin: 0;
  font-size: 17px;
}

.owner-heading span {
  flex: 0 0 auto;
  padding: 5px 10px;
  color: #0f5f71;
  background: #eef8f7;
  border: 1px solid #d8ebe9;
  border-radius: 999px;
  font-weight: 700;
}

.owner-card p {
  margin: 0 0 14px;
  color: var(--muted);
  line-height: 1.7;
}

.redis-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 0.75fr);
  gap: 18px;
  margin-bottom: 18px;
}

.redis-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.redis-overview div {
  padding: 14px;
  background: var(--panel-soft);
  border: 1px solid var(--line);
  border-radius: 8px;
}

.redis-overview span,
.redis-overview strong {
  display: block;
}

.redis-overview span {
  color: var(--muted);
  font-size: 13px;
}

.redis-overview strong {
  margin-top: 8px;
  font-size: 24px;
}

.redis-note {
  margin: 14px 0 0;
  color: var(--muted);
  line-height: 1.7;
}

.talking-points {
  margin: 0;
  padding-left: 18px;
  color: #334155;
  line-height: 1.9;
}

.redis-warning {
  margin-bottom: 18px;
  padding: 12px 14px;
  color: #9a3412;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 8px;
}

@media (max-width: 1200px) {
  .guide-grid,
  .redis-owner-grid,
  .redis-grid,
  .redis-overview {
    grid-template-columns: 1fr;
  }
}
</style>
