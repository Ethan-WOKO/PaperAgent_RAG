<template>
  <NConfigProvider :theme="naiveTheme" :theme-overrides="themeOverrides">
    <NLoadingBarProvider>
      <NDialogProvider>
        <NNotificationProvider>
          <NMessageProvider>
            <RouterView />
          </NMessageProvider>
        </NNotificationProvider>
      </NDialogProvider>
    </NLoadingBarProvider>
  </NConfigProvider>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import {
  NConfigProvider,
  NDialogProvider,
  NLoadingBarProvider,
  NMessageProvider,
  NNotificationProvider,
  darkTheme,
  lightTheme,
} from 'naive-ui';
import { RouterView } from 'vue-router';
import { useTheme } from '@/composables/useTheme';

const { isDark } = useTheme();

const naiveTheme = computed(() => (isDark.value ? darkTheme : lightTheme));

const themeOverrides = computed(() => ({
  common: {
    primaryColor: '#10a37f',
    primaryColorHover: '#0f8f70',
    primaryColorPressed: '#0d7d62',
    primaryColorSuppl: '#10a37f',
    borderRadius: '12px',
    ...(isDark.value
      ? {
          bodyColor: '#111318',
          cardColor: '#171a21',
          modalColor: '#171a21',
          popoverColor: '#171a21',
          tableColor: '#171a21',
          borderColor: '#2a2f3a',
          dividerColor: '#2a2f3a',
          textColorBase: '#f3f4f6',
          textColor1: '#f3f4f6',
          textColor2: '#c4cad4',
          textColor3: '#8b93a7',
          inputColor: '#111318',
        }
      : {
          bodyColor: '#f7f7f8',
          cardColor: '#ffffff',
          modalColor: '#ffffff',
          popoverColor: '#ffffff',
          tableColor: '#ffffff',
          borderColor: '#e5e7eb',
          dividerColor: '#e5e7eb',
          textColorBase: '#111827',
          textColor1: '#111827',
          textColor2: '#4b5563',
          textColor3: '#6b7280',
          inputColor: '#ffffff',
        }),
  },
  Card: {
    borderRadius: '18px',
  },
  Input: {
    borderRadius: '14px',
  },
  Button: {
    borderRadiusMedium: '12px',
  },
}));
</script>
