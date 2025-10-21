import { defineConfig } from '@rsbuild/core'
import { pluginReact } from '@rsbuild/plugin-react'

export default {
	plugins: [pluginReact()],
	server: {
		proxy: {
			'/api': 'http://localhost:8080'
		}
	}
}

