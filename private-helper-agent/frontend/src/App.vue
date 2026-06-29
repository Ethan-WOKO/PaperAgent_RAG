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
    primaryColor: isDark.value ? '#ffffff' : '#171717',
    primaryColorHover: isDark.value ? '#e5e5e5' : '#000000',
    primaryColorPressed: isDark.value ? '#d4d4d4' : '#000000',
    primaryColorSuppl: '#0070f3',
    borderRadius: '6px',
    fontFamily: 'Geist, Inter, Arial, "PingFang SC", "Microsoft YaHei", sans-serif',
    fontFamilyMono: '"Geist Mono", ui-monospace, SFMono-Regular, Menlo, monospace',
    ...(isDark.value
      ? {
          bodyColor: '#000000',
          cardColor: '#0a0a0a',
          modalColor: '#0a0a0a',
          popoverColor: '#0a0a0a',
          tableColor: '#0a0a0a',
          borderColor: '#2e2e2e',
          dividerColor: '#2e2e2e',
          textColorBase: '#ededed',
          textColor1: '#ededed',
          textColor2: '#a1a1a1',
          textColor3: '#737373',
          inputColor: '#000000',
          hoverColor: '#111111',
        }
      : {
          bodyColor: '#fafafa',
          cardColor: '#ffffff',
          modalColor: '#ffffff',
          popoverColor: '#ffffff',
          tableColor: '#ffffff',
          borderColor: '#ebebeb',
          dividerColor: '#ebebeb',
          textColorBase: '#171717',
          textColor1: '#171717',
          textColor2: '#4d4d4d',
          textColor3: '#8f8f8f',
          inputColor: '#ffffff',
          hoverColor: '#f2f2f2',
        }),
  },
  Card: {
    borderRadius: '12px',
  },
  Input: {
    borderRadius: '6px',
  },
  Button: {
    borderRadiusMedium: '6px',
    borderRadiusLarge: '100px',
  },
}));
</script>
