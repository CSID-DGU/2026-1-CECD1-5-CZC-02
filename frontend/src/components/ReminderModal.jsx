import { X, Bell } from 'lucide-react';

export function ReminderModal({ isOpen, onClose, reminder }) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg w-full max-w-md border border-gray-200 shadow-xl">
        {/* Header */}
        <div className="border-b border-gray-200 px-5 py-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Bell className="w-4 h-4 text-blue-500" />
            <h3 className="text-sm text-gray-800 text-left">일정 알림</h3>
          </div>
          <button
            onClick={onClose}
            className="p-1 hover:bg-gray-100 rounded"
          >
            <X className="w-4 h-4 text-gray-600" />
          </button>
        </div>

        {/* Content */}
        <div className="px-5 py-5 space-y-4">
          <div className="bg-blue-50/50 border border-blue-100 rounded-md p-4">
            <p className="text-xs text-blue-600 mb-2 text-left">예정된 일정</p>
            <p className="text-base text-gray-900 font-medium text-left">{reminder.title}</p>
          </div>

          <div>
            <label className="block text-xs text-gray-500 mb-1.5 text-left">일정 종류</label>
            <span className="inline-block px-3 py-1 text-sm bg-blue-100 text-blue-700 rounded">
              {reminder.type}
            </span>
          </div>

          <div>
            <label className="block text-xs text-gray-500 mb-1.5 text-left">날짜 / 시간</label>
            <p className="text-sm text-gray-800 text-left">{reminder.date} {reminder.time}</p>
          </div>

          <div>
            <label className="block text-xs text-gray-500 mb-1.5 text-left">상세 내용</label>
            <p className="text-sm text-gray-700 text-left">{reminder.content}</p>
          </div>
        </div>

        {/* Footer */}
        <div className="border-t border-gray-200 px-5 py-4">
          <button
            onClick={onClose}
            className="w-full bg-blue-500 hover:bg-blue-600 text-white py-2 rounded text-sm"
          >
            확인
          </button>
        </div>
      </div>
    </div>
  );
}
