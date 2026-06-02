import { X, Bell } from 'lucide-react';

export function ReminderModal({ isOpen, onClose, reminder }) {
  if (!isOpen || !reminder) return null;

  const isUrgent = reminder.kind === 'THIRTY_MINUTES';

  return (
    <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg w-full max-w-md border border-gray-200 shadow-xl">
        <div className="border-b border-gray-200 px-5 py-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Bell className={`w-4 h-4 ${isUrgent ? 'text-red-500' : 'text-blue-500'}`} />
            <h3 className="text-sm text-gray-800 text-left">
              {isUrgent ? '30분 전 일정 알림' : '일정 알림'}
            </h3>
          </div>
          <button
            onClick={onClose}
            className="p-1 hover:bg-gray-100 rounded"
          >
            <X className="w-4 h-4 text-gray-600" />
          </button>
        </div>

        <div className="px-5 py-5 space-y-4">
          {isUrgent && (
            <div className="bg-red-50 border border-red-200 rounded-md p-3 text-left">
              <p className="text-xs font-semibold text-red-600 mb-1">30분 전 알림</p>
              <p className="text-sm text-red-700">곧 시작되는 일정입니다.</p>
            </div>
          )}

          <div className={`${isUrgent ? 'bg-red-50/50 border-red-100' : 'bg-blue-50/50 border-blue-100'} border rounded-md p-4`}>
            <p className={`text-xs mb-2 text-left ${isUrgent ? 'text-red-600' : 'text-blue-600'}`}>
              {reminder.label || '예정된 일정'}
            </p>
            <p className="text-base text-gray-900 font-medium text-left">{reminder.title}</p>
          </div>

          {reminder.items?.length > 0 ? (
            <div className="space-y-2">
              {reminder.items.map((item) => (
                <div key={`${item.id}-${item.dateTime}`} className="border border-gray-100 rounded p-2 text-left">
                  <p className="text-sm text-gray-800">{item.title}</p>
                  <p className="text-xs text-gray-500 mt-1">{item.date} {item.time}</p>
                </div>
              ))}
            </div>
          ) : (
            <>
              <div>
                <label className="block text-xs text-gray-500 mb-1.5 text-left">일정 종류</label>
                <span className="inline-block px-3 py-1 text-sm bg-blue-100 text-blue-700 rounded">
                  {reminder.type || '일정'}
                </span>
              </div>

              <div>
                <label className="block text-xs text-gray-500 mb-1.5 text-left">날짜 / 시간</label>
                <p className="text-sm text-gray-800 text-left">{reminder.date} {reminder.time}</p>
              </div>

              <div>
                <label className="block text-xs text-gray-500 mb-1.5 text-left">상세 내용</label>
                <p className="text-sm text-gray-700 text-left whitespace-pre-wrap break-words">{reminder.content || '해당 없음'}</p>
              </div>
            </>
          )}
        </div>

        <div className="border-t border-gray-200 px-5 py-4">
          <button
            onClick={onClose}
            className={`w-full text-white py-2 rounded text-sm ${
              isUrgent ? 'bg-red-500 hover:bg-red-600' : 'bg-blue-500 hover:bg-blue-600'
            }`}
          >
            확인
          </button>
        </div>
      </div>
    </div>
  );
}
