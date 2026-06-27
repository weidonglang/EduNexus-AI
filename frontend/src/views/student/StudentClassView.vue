<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { studentClassInfoApi, type StudentClassInfo } from '@/api/student'

const loading = ref(false)
const classInfo = ref<StudentClassInfo>()

const members = computed(() => classInfo.value?.members ?? [])

onMounted(loadClassInfo)

async function loadClassInfo() {
  loading.value = true
  try {
    classInfo.value = (await studentClassInfoApi()).data
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <PageHeader title="我的班级" description="查看本人班级、班主任和本班成员基础信息。" />

  <section v-loading="loading" class="admin-stack">
    <article v-if="classInfo" class="work-panel">
      <div class="panel-title-row">
        <h2>{{ classInfo.className }}</h2>
        <el-tag type="success">{{ classInfo.studentCount }} 人</el-tag>
      </div>
      <div class="info-fields three-columns">
        <div><strong>学院：</strong><span>{{ classInfo.college }}</span></div>
        <div><strong>专业：</strong><span>{{ classInfo.major }}</span></div>
        <div><strong>年级：</strong><span>{{ classInfo.grade }}</span></div>
        <div><strong>班主任：</strong><span>{{ classInfo.homeroomTeacherName || classInfo.advisor || '未指定' }}</span></div>
        <div><strong>负责人：</strong><span>{{ classInfo.advisor || '-' }}</span></div>
        <div><strong>可见字段：</strong><span>学号、姓名、学籍状态</span></div>
      </div>
    </article>

    <article class="work-panel">
      <div class="panel-title-row">
        <h2>本班成员</h2>
        <el-button @click="loadClassInfo">刷新</el-button>
      </div>
      <el-table :data="members" empty-text="暂无本班成员">
        <el-table-column prop="studentNo" label="学号" width="150" />
        <el-table-column prop="name" label="姓名" min-width="140" />
        <el-table-column prop="status" label="学籍状态" width="120" />
      </el-table>
    </article>
  </section>
</template>
